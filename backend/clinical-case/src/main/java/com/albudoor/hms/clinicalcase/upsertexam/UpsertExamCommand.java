package com.albudoor.hms.clinicalcase.upsertexam;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Idempotent upsert payload — the doctor's full exam state is sent on every autosave.
 * Server replaces the in-place record.
 */
public record UpsertExamCommand(
        @NotNull UUID visitId,
        VitalsPart vitals,
        @Size(max = 1000) String chiefComplaint,
        @Size(max = 4000) String historyOfPresentIllness,
        @Size(max = 4000) String examinationNotes,
        @Size(max = 4000) String plan,
        @Size(max = 1000) String referralInstructions,
        List<Diagnosis> diagnoses,
        List<Prescription> prescriptions
) {

    public record VitalsPart(
            Integer systolicBp, Integer diastolicBp,
            Integer heartRate, Integer respiratoryRate,
            BigDecimal temperatureC, Integer oxygenSaturation,
            BigDecimal weightKg, BigDecimal heightCm,
            String notes
    ) {}

    public record Diagnosis(
            @Size(max = 30) String code,
            @NotBlank @Size(max = 500) String description,
            boolean primary,
            @Size(max = 1000) String notes
    ) {}

    public record Prescription(
            UUID drugServiceItemId,
            @Size(max = 50) String drugCode,
            @NotBlank @Size(max = 300) String drugName,
            @Size(max = 100) String strength,
            @Size(max = 100) String dose,
            @Size(max = 100) String frequency,
            @Size(max = 100) String duration,
            @Size(max = 50) String route,
            @Size(max = 500) String notes
    ) {}
}
