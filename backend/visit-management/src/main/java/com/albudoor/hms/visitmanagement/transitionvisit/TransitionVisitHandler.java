package com.albudoor.hms.visitmanagement.transitionvisit;

import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Generic status-change endpoint. The aggregate's transition graph enforces validity, so
 * misuse (e.g. CREATED → COMPLETED) returns 422 from {@link com.albudoor.hms.platform.web.GlobalExceptionHandler}.
 */
@Service
public class TransitionVisitHandler {

    private final VisitRepository visits;
    private final ApplicationEventPublisher events;

    public TransitionVisitHandler(VisitRepository visits, ApplicationEventPublisher events) {
        this.visits = visits;
        this.events = events;
    }

    @Transactional
    public Visit handle(UUID id, TransitionVisitCommand cmd) {
        Visit visit = visits.findById(id)
                .orElseThrow(() -> new NotFoundException("Visit not found: " + id));
        if (cmd.target() == VisitStatus.CANCELLED) {
            visit.cancel(cmd.reason());
            cancelOpenChildren(visit, cmd.reason());
        } else {
            visit.transitionTo(cmd.target());
        }
        visit.pullDomainEvents().forEach(events::publishEvent);
        return visit;
    }

    /**
     * When a parent visit is cancelled, cascade-cancel any of its still-open forwarded sub-visits
     * (lab/imaging/eco orders) so they don't linger in the receiving department's queue. Children
     * already terminal (COMPLETED/CANCELLED) are skipped, so this is safe to run unconditionally and
     * never double-cancels. Forwarded children themselves have no children, so no deep recursion.
     */
    private void cancelOpenChildren(Visit parent, String reason) {
        String childReason = reason != null ? reason : "Parent visit cancelled";
        for (Visit child : visits.findAllByParentVisitIdOrderByStartedAtDesc(parent.getId())) {
            if (!child.getStatus().isTerminal()) {
                child.cancel(childReason);
                child.pullDomainEvents().forEach(events::publishEvent);
            }
        }
    }
}
