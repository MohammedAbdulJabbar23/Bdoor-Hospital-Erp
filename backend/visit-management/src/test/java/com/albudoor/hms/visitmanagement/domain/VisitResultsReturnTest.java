package com.albudoor.hms.visitmanagement.domain;

import com.albudoor.hms.platform.exception.DomainException;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class VisitResultsReturnTest {

    private Visit forwardedParentInProgress() {
        Visit p = Visit.createDirect("V-1", UUID.randomUUID(), "MRN1", "Pat", VisitType.PREMATURE,
                VisitOrigin.DIRECT_NEW, null);
        p.pullDomainEvents();
        p.transitionTo(VisitStatus.AWAITING_PAYMENT);
        p.transitionTo(VisitStatus.IN_PROGRESS);
        p.pullDomainEvents();
        return p;
    }

    @Test
    void receiveResults_whenParentInProgress_doesNotTransition_butEmitsReturned() {
        Visit parent = forwardedParentInProgress();
        UUID childId = UUID.randomUUID();
        parent.receiveResultsFromChild(childId, VisitType.LABORATORY, "WBC normal");
        assertThat(parent.getStatus()).isEqualTo(VisitStatus.IN_PROGRESS); // not paused → stays
        assertThat(parent.getResultsSummary()).isEqualTo("WBC normal");
        assertThat(parent.pullDomainEvents())
                .anyMatch(e -> e instanceof VisitReturnedEvent);
    }

    @Test
    void receiveResults_stampsResultsLastUpdatedAt_soTheBellCanSurfaceTheOriginatingVisit() {
        Visit parent = forwardedParentInProgress();
        assertThat(parent.getResultsLastUpdatedAt()).isNull();
        parent.receiveResultsFromChild(UUID.randomUUID(), VisitType.LABORATORY, "WBC normal");
        assertThat(parent.getResultsLastUpdatedAt()).isNotNull();
    }

    @Test
    void completeForwardedWith_whenChildStillCreated_throwsClearSubvisitError_notGenericTransition() {
        Visit parent = forwardedParentInProgress();
        Visit child = Visit.createForwarded("V-2", UUID.randomUUID(), "MRN1", "Pat",
                VisitType.LABORATORY, parent.getId(), VisitType.PREMATURE, null);
        // Child is still CREATED (payment not approved) — returning results must fail with a
        // clear domain error, not the generic INVALID_VISIT_TRANSITION.
        assertThatThrownBy(() -> child.completeForwardedWith("results"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo("SUBVISIT_NOT_IN_PROGRESS"));
    }

    @Test
    void completeForwardedWith_whenChildInProgress_completesNormally() {
        Visit parent = forwardedParentInProgress();
        Visit child = Visit.createForwarded("V-3", UUID.randomUUID(), "MRN1", "Pat",
                VisitType.LABORATORY, parent.getId(), VisitType.PREMATURE, null);
        child.transitionTo(VisitStatus.AWAITING_PAYMENT);
        child.transitionTo(VisitStatus.IN_PROGRESS);
        child.pullDomainEvents();
        child.completeForwardedWith("WBC normal");
        assertThat(child.getStatus()).isEqualTo(VisitStatus.COMPLETED);
        assertThat(child.getResultsSummary()).isEqualTo("WBC normal");
        assertThat(child.getResultsLastUpdatedAt()).isNotNull();
    }

    @Test
    void receiveResults_whenParentAwaitingResults_resumesToInProgress() {
        Visit parent = forwardedParentInProgress();
        parent.markAwaitingResults(); // doctor pattern
        parent.pullDomainEvents();
        parent.receiveResultsFromChild(UUID.randomUUID(), VisitType.LABORATORY, "ok");
        assertThat(parent.getStatus()).isEqualTo(VisitStatus.IN_PROGRESS);
    }

    @Test
    void receiveResults_whenParentCompleted_doesNotOverwriteSummary_norEmitsReturned() {
        Visit parent = forwardedParentInProgress();
        parent.receiveResultsFromChild(UUID.randomUUID(), VisitType.LABORATORY, "first result");
        parent.transitionTo(VisitStatus.COMPLETED);
        parent.pullDomainEvents();

        // A late result returns to the now-closed parent: must not mutate the closed aggregate.
        parent.receiveResultsFromChild(UUID.randomUUID(), VisitType.RADIOLOGY, "LATE result");

        assertThat(parent.getStatus()).isEqualTo(VisitStatus.COMPLETED);
        assertThat(parent.getResultsSummary()).isEqualTo("first result"); // not overwritten
        assertThat(parent.pullDomainEvents())
                .as("no VisitReturnedEvent for a result returning to a closed visit")
                .noneMatch(e -> e instanceof VisitReturnedEvent);
    }

    @Test
    void receiveResults_whenParentCancelled_isANoOp() {
        Visit parent = forwardedParentInProgress();
        parent.cancel("patient left");
        parent.pullDomainEvents();

        parent.receiveResultsFromChild(UUID.randomUUID(), VisitType.LABORATORY, "late lab");

        assertThat(parent.getStatus()).isEqualTo(VisitStatus.CANCELLED);
        assertThat(parent.getResultsSummary()).isNull();
        assertThat(parent.pullDomainEvents())
                .noneMatch(e -> e instanceof VisitReturnedEvent);
    }
}
