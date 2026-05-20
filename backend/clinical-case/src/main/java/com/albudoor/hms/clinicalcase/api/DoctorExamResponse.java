package com.albudoor.hms.clinicalcase.api;

import com.albudoor.hms.clinicalcase.domain.DiagnosisEntry;
import com.albudoor.hms.clinicalcase.domain.DoctorExam;
import com.albudoor.hms.clinicalcase.domain.ExamStatus;
import com.albudoor.hms.clinicalcase.domain.PrescriptionEntry;
import com.albudoor.hms.clinicalcase.domain.Vitals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DoctorExamResponse(
        UUID id,
        UUID visitId,
        String visitDisplayId,
        UUID patientId,
        String patientMrn,
        String patientName,
        UUID doctorId,
        String doctorName,
        VitalsPart vitals,
        String chiefComplaint,
        String historyOfPresentIllness,
        String examinationNotes,
        String plan,
        String referralInstructions,
        List<DiagnosisPart> diagnoses,
        List<PrescriptionPart> prescriptions,
        ExamStatus status,
        Instant finalizedAt,
        UUID finalizedBy,
        Instant createdAt,
        Instant updatedAt
) {

    public record VitalsPart(
            Integer systolicBp, Integer diastolicBp,
            Integer heartRate, Integer respiratoryRate,
            BigDecimal temperatureC, Integer oxygenSaturation,
            BigDecimal weightKg, BigDecimal heightCm, BigDecimal bmi,
            String notes
    ) {}

    public record DiagnosisPart(String code, String description, boolean primary, String notes) {}

    public record PrescriptionPart(
            UUID drugServiceItemId, String drugCode, String drugName, String strength,
            String dose, String frequency, String duration, String route, String notes
    ) {}

    public static DoctorExamResponse from(DoctorExam e) {
        Vitals v = e.getVitals() != null ? e.getVitals() : Vitals.empty();
        VitalsPart vp = new VitalsPart(
                v.getSystolicBp(), v.getDiastolicBp(),
                v.getHeartRate(), v.getRespiratoryRate(),
                v.getTemperatureC(), v.getOxygenSaturation(),
                v.getWeightKg(), v.getHeightCm(), v.bmi(),
                v.getNotes()
        );
        List<DiagnosisPart> dx = e.getDiagnoses().stream()
                .map(d -> new DiagnosisPart(d.getCode(), d.getDescription(), d.isPrimary(), d.getNotes()))
                .toList();
        List<PrescriptionPart> rx = e.getPrescriptions().stream()
                .map(p -> new PrescriptionPart(
                        p.getDrugServiceItemId(), p.getDrugCode(), p.getDrugName(), p.getStrength(),
                        p.getDose(), p.getFrequency(), p.getDuration(), p.getRoute(), p.getNotes()))
                .toList();

        return new DoctorExamResponse(
                e.getId(), e.getVisitId(), e.getVisitDisplayId(),
                e.getPatientId(), e.getPatientMrn(), e.getPatientName(),
                e.getDoctorId(), e.getDoctorName(),
                vp,
                e.getChiefComplaint(), e.getHistoryOfPresentIllness(),
                e.getExaminationNotes(), e.getPlan(), e.getReferralInstructions(),
                dx, rx,
                e.getStatus(), e.getFinalizedAt(), e.getFinalizedBy(),
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    public static DiagnosisEntry toEntry(DiagnosisPart p) {
        return new DiagnosisEntry(p.code(), p.description(), p.primary(), p.notes());
    }

    public static PrescriptionEntry toEntry(PrescriptionPart p) {
        return new PrescriptionEntry(
                p.drugServiceItemId(), p.drugCode(), p.drugName(), p.strength(),
                p.dose(), p.frequency(), p.duration(), p.route(), p.notes());
    }
}
