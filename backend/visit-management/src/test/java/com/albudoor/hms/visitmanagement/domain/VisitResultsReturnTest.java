package com.albudoor.hms.visitmanagement.domain;

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
    void receiveResults_whenParentAwaitingResults_resumesToInProgress() {
        Visit parent = forwardedParentInProgress();
        parent.markAwaitingResults(); // doctor pattern
        parent.pullDomainEvents();
        parent.receiveResultsFromChild(UUID.randomUUID(), VisitType.LABORATORY, "ok");
        assertThat(parent.getStatus()).isEqualTo(VisitStatus.IN_PROGRESS);
    }
}
