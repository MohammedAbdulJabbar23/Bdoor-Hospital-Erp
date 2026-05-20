package com.albudoor.hms.pharmacy.domain;

import com.albudoor.hms.platform.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record DispenseGivenEvent(
        UUID eventId,
        Instant occurredAt,
        UUID dispenseId,
        UUID visitId,
        UUID patientId,
        UUID givenByUserId
) implements DomainEvent {
    public static DispenseGivenEvent of(PharmacyDispense d) {
        return new DispenseGivenEvent(UUID.randomUUID(), Instant.now(),
                d.getId(), d.getVisitId(), d.getPatientId(), d.getGivenByUserId());
    }
}
