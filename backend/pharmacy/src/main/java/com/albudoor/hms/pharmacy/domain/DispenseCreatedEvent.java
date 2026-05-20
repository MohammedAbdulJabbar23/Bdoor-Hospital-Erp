package com.albudoor.hms.pharmacy.domain;

import com.albudoor.hms.platform.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record DispenseCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID dispenseId,
        UUID examId,
        UUID visitId,
        UUID patientId,
        int billableLineCount
) implements DomainEvent {
    public static DispenseCreatedEvent of(PharmacyDispense d, int billable) {
        return new DispenseCreatedEvent(UUID.randomUUID(), Instant.now(),
                d.getId(), d.getExamId(), d.getVisitId(), d.getPatientId(), billable);
    }
}
