package com.albudoor.hms.visitmanagement.returnvisit;

import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Completes a forwarded sub-visit and feeds its results summary back to the parent.
 * Emits {@code VisitReturnedEvent} on the parent so the originating department's queue
 * lights up.
 */
@Service
public class ReturnVisitHandler {

    private final VisitRepository visits;
    private final ApplicationEventPublisher events;

    public ReturnVisitHandler(VisitRepository visits, ApplicationEventPublisher events) {
        this.visits = visits;
        this.events = events;
    }

    @Transactional
    public ReturnResult handle(UUID childId, ReturnVisitCommand cmd) {
        Visit child = visits.findById(childId)
                .orElseThrow(() -> new NotFoundException("Visit not found: " + childId));
        if (child.getParentVisitId() == null) {
            throw new DomainException("NOT_FORWARDED", "Visit is not a forwarded sub-visit");
        }
        Visit parent = visits.findById(child.getParentVisitId())
                .orElseThrow(() -> new NotFoundException(
                        "Parent visit not found: " + child.getParentVisitId()));

        child.completeForwardedWith(cmd.resultsSummary());
        parent.receiveResultsFromChild(child.getId(), child.getVisitType(), cmd.resultsSummary());

        child.pullDomainEvents().forEach(events::publishEvent);
        parent.pullDomainEvents().forEach(events::publishEvent);

        return new ReturnResult(parent, child);
    }

    public record ReturnResult(Visit parent, Visit child) {}
}
