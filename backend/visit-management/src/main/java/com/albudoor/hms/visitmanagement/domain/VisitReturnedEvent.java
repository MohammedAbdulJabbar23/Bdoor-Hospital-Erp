package com.albudoor.hms.visitmanagement.domain;

import com.albudoor.hms.platform.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a forwarded sub-visit completes and its results return to the parent visit.
 * Picked up by the notification module to alert the originating department's queue.
 */
public record VisitReturnedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID parentVisitId,
        UUID childVisitId,
        VisitType originatingType,
        String resultsSummary
) implements DomainEvent {

    public static VisitReturnedEvent of(UUID parent, UUID child, VisitType origin, String summary) {
        return new VisitReturnedEvent(UUID.randomUUID(), Instant.now(), parent, child, origin, summary);
    }
}
