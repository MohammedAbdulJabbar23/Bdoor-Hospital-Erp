package com.albudoor.hms.visitmanagement.domain;

import com.albudoor.hms.platform.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record VisitForwardedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID parentVisitId,
        UUID childVisitId,
        VisitType targetType
) implements DomainEvent {

    public static VisitForwardedEvent of(UUID parent, UUID child, VisitType target) {
        return new VisitForwardedEvent(UUID.randomUUID(), Instant.now(), parent, child, target);
    }
}
