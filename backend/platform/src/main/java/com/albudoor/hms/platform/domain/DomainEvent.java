package com.albudoor.hms.platform.domain;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();

    default String eventType() {
        return getClass().getSimpleName();
    }
}
