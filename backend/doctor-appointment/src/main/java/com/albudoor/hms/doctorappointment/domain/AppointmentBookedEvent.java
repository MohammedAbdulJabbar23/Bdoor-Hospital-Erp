package com.albudoor.hms.doctorappointment.domain;

import com.albudoor.hms.platform.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record AppointmentBookedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID appointmentId,
        UUID doctorId,
        UUID patientId,
        UUID visitId,
        AppointmentType type
) implements DomainEvent {
    public static AppointmentBookedEvent of(Appointment a) {
        return new AppointmentBookedEvent(UUID.randomUUID(), Instant.now(),
                a.getId(), a.getDoctorId(), a.getPatientId(), a.getVisitId(), a.getType());
    }
}
