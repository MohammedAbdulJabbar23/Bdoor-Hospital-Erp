package com.albudoor.hms.visitmanagement.forwardvisit;

import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitForwardedEvent;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import com.albudoor.hms.visitmanagement.infrastructure.VisitIdGenerator;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ForwardVisitHandler {

    private final VisitRepository visits;
    private final VisitIdGenerator idGenerator;
    private final ApplicationEventPublisher events;

    public ForwardVisitHandler(
            VisitRepository visits,
            VisitIdGenerator idGenerator,
            ApplicationEventPublisher events
    ) {
        this.visits = visits;
        this.idGenerator = idGenerator;
        this.events = events;
    }

    /**
     * Creates a forwarded sub-visit at the target department and moves the parent to
     * AWAITING_RESULTS. Both visits are saved in a single transaction.
     */
    @Transactional
    public ForwardResult handle(UUID parentVisitId, ForwardVisitCommand cmd) {
        return handle(parentVisitId, cmd, true);
    }

    /**
     * @param pauseParent true for the doctor "pause-and-wait" flow (parent → AWAITING_RESULTS);
     *                    false for bed-stay orders (parent stays IN_PROGRESS, concurrent orders allowed).
     */
    @Transactional
    public ForwardResult handle(UUID parentVisitId, ForwardVisitCommand cmd, boolean pauseParent) {
        Visit parent = visits.findById(parentVisitId)
                .orElseThrow(() -> new NotFoundException("Visit not found: " + parentVisitId));

        if (cmd.targetType() == parent.getVisitType()) {
            throw new DomainException("INVALID_FORWARD_TARGET",
                    "Cannot forward a visit to its own visit type");
        }

        if (pauseParent && (parent.getVisitType() == VisitType.PREMATURE
                || parent.getVisitType() == VisitType.EMERGENCY)) {
            throw new DomainException("INVALID_FORWARD_SOURCE",
                    "Bed-stay visits (premature/emergency) must order via the department order flow, not the pausing forward");
        }

        Visit child = Visit.createForwarded(
                idGenerator.next(),
                parent.getPatientId(),
                parent.getPatientMrn(),
                parent.getPatientName(),
                cmd.targetType(),
                parent.getId(),
                parent.getVisitType(),
                cmd.note()
        );
        visits.save(child);
        if (pauseParent) {
            parent.markAwaitingResults();
        }

        // Drain events from both aggregates first, then publish in order so handlers see
        // the parent's status change before reacting to the forward.
        parent.pullDomainEvents().forEach(events::publishEvent);
        child.pullDomainEvents().forEach(events::publishEvent);
        events.publishEvent(VisitForwardedEvent.of(parent.getId(), child.getId(), cmd.targetType()));

        return new ForwardResult(parent, child);
    }

    public record ForwardResult(Visit parent, Visit child) {}
}
