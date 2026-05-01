package com.albudoor.hms.visitmanagement.domain;

import com.albudoor.hms.platform.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record VisitStatusChangedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID visitId,
        VisitStatus from,
        VisitStatus to
) implements DomainEvent {

    public static VisitStatusChangedEvent of(UUID visitId, VisitStatus from, VisitStatus to) {
        return new VisitStatusChangedEvent(UUID.randomUUID(), Instant.now(), visitId, from, to);
    }
}
