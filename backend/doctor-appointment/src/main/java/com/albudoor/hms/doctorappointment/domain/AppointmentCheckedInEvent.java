package com.albudoor.hms.doctorappointment.domain;

import com.albudoor.hms.platform.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Fired when a receptionist marks a booked appointment as checked-in. The consult-charge
 * bridge listens for this event to bill the patient and park the visit at AWAITING_PAYMENT.
 */
public record AppointmentCheckedInEvent(
        UUID eventId,
        Instant occurredAt,
        UUID appointmentId,
        UUID doctorId,
        UUID patientId,
        UUID visitId
) implements DomainEvent {
    public static AppointmentCheckedInEvent of(Appointment a) {
        return new AppointmentCheckedInEvent(UUID.randomUUID(), Instant.now(),
                a.getId(), a.getDoctorId(), a.getPatientId(), a.getVisitId());
    }
}
