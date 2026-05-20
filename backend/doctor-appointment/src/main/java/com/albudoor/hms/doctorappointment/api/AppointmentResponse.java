package com.albudoor.hms.doctorappointment.api;

import com.albudoor.hms.doctorappointment.domain.Appointment;
import com.albudoor.hms.doctorappointment.domain.AppointmentStatus;
import com.albudoor.hms.doctorappointment.domain.AppointmentType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UUID doctorId,
        String doctorName,
        UUID patientId,
        String patientMrn,
        String patientName,
        UUID visitId,
        LocalDateTime scheduledFor,
        LocalDate scheduledDate,
        int durationMinutes,
        AppointmentType type,
        AppointmentStatus status,
        String notes,
        String cancellationReason,
        Instant checkedInAt,
        Instant completedAt
) {
    public static AppointmentResponse from(Appointment a) {
        return new AppointmentResponse(
                a.getId(), a.getDoctorId(), a.getDoctorName(),
                a.getPatientId(), a.getPatientMrn(), a.getPatientName(),
                a.getVisitId(),
                a.getScheduledFor(), a.getScheduledDate(), a.getDurationMinutes(),
                a.getType(), a.getStatus(),
                a.getNotes(), a.getCancellationReason(),
                a.getCheckedInAt(), a.getCompletedAt()
        );
    }
}
