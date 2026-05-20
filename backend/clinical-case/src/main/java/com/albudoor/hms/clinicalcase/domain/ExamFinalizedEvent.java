package com.albudoor.hms.clinicalcase.domain;

import com.albudoor.hms.platform.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record ExamFinalizedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID examId,
        UUID visitId,
        UUID doctorId,
        UUID patientId,
        int diagnosisCount,
        int prescriptionCount
) implements DomainEvent {
    public static ExamFinalizedEvent of(DoctorExam e) {
        return new ExamFinalizedEvent(
                UUID.randomUUID(), Instant.now(),
                e.getId(), e.getVisitId(), e.getDoctorId(), e.getPatientId(),
                e.getDiagnoses().size(), e.getPrescriptions().size());
    }
}
