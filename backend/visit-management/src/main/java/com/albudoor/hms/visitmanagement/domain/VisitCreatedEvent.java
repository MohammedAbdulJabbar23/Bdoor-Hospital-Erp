package com.albudoor.hms.visitmanagement.domain;

import com.albudoor.hms.platform.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record VisitCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID visitId,
        String visitDisplayId,
        UUID patientId,
        VisitType visitType,
        VisitOrigin origin,
        UUID assignedDoctorId
) implements DomainEvent {

    public static VisitCreatedEvent of(Visit v) {
        return new VisitCreatedEvent(
                UUID.randomUUID(), Instant.now(),
                v.getId(), v.getVisitDisplayId(), v.getPatientId(), v.getVisitType(), v.getOrigin(),
                v.getAssignedDoctorId());
    }
}
