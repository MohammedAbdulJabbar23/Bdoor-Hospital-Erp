package com.albudoor.hms.doctorappointment.domain;

import com.albudoor.hms.platform.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record AppointmentCancelledEvent(
        UUID eventId,
        Instant occurredAt,
        UUID appointmentId,
        UUID doctorId,
        UUID visitId,
        String reason
) implements DomainEvent {
    public static AppointmentCancelledEvent of(Appointment a) {
        return new AppointmentCancelledEvent(UUID.randomUUID(), Instant.now(),
                a.getId(), a.getDoctorId(), a.getVisitId(), a.getCancellationReason());
    }
}
