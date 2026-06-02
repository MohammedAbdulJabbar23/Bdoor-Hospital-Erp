package com.albudoor.hms.clinicalcase.upsertexam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
        @Valid VitalsPart vitals,
        @Size(max = 1000) String chiefComplaint,
        @Size(max = 4000) String historyOfPresentIllness,
        @Size(max = 4000) String examinationNotes,
        @Size(max = 4000) String plan,
        @Size(max = 1000) String referralInstructions,
        @Valid List<Diagnosis> diagnoses,
        @Valid List<Prescription> prescriptions
) {

    /**
     * Vitals bounds are clinically sane outer limits — they reject obvious data-entry errors
     * and DB-overflow values (which previously surfaced as a 409) while accepting every real
     * measurement. All fields stay nullable so a partial set is still valid.
     */
    public record VitalsPart(
            @Min(0) @Max(300) Integer systolicBp,
            @Min(0) @Max(300) Integer diastolicBp,
            @Min(0) @Max(300) Integer heartRate,
            @Min(0) @Max(120) Integer respiratoryRate,
            @DecimalMin("25.0") @DecimalMax("45.0") BigDecimal temperatureC,
            @Min(0) @Max(100) Integer oxygenSaturation,
            @DecimalMin("0.0") @DecimalMax("500.0") BigDecimal weightKg,
            @DecimalMin("0.0") @DecimalMax("300.0") BigDecimal heightCm,
            @Size(max = 500) String notes
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
