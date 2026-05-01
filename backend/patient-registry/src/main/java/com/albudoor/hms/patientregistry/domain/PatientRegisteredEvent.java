package com.albudoor.hms.patientregistry.domain;

import com.albudoor.hms.platform.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PatientRegisteredEvent(
        UUID eventId,
        Instant occurredAt,
        UUID patientId,
        String mrn,
        PatientType type
) implements DomainEvent {

    public static PatientRegisteredEvent of(UUID patientId, String mrn, PatientType type) {
        return new PatientRegisteredEvent(UUID.randomUUID(), Instant.now(), patientId, mrn, type);
    }
}
