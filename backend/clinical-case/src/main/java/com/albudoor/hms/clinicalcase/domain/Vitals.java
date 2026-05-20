package com.albudoor.hms.clinicalcase.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Patient vitals snapshot taken at the start of the consultation.
 *
 * <p>All fields are nullable — a doctor may record a partial set (e.g. just BP and HR
 * for a follow-up). BMI is derived; the response includes it so the UI doesn't need
 * to recompute.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Vitals {

    @Column(name = "bp_systolic")
    private Integer systolicBp;

    @Column(name = "bp_diastolic")
    private Integer diastolicBp;

    @Column(name = "heart_rate")
    private Integer heartRate;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "temperature_c", precision = 4, scale = 1)
    private BigDecimal temperatureC;

    @Column(name = "oxygen_saturation")
    private Integer oxygenSaturation;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "height_cm", precision = 5, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "vitals_notes", length = 500)
    private String notes;

    public static Vitals empty() {
        return new Vitals(null, null, null, null, null, null, null, null, null);
    }

    /** BMI = weight (kg) / height (m)². Returns null if either input is missing. */
    public BigDecimal bmi() {
        if (weightKg == null || heightCm == null || heightCm.signum() <= 0) return null;
        BigDecimal heightM = heightCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal heightSq = heightM.multiply(heightM);
        if (heightSq.signum() <= 0) return null;
        return weightKg.divide(heightSq, 1, RoundingMode.HALF_UP);
    }
}
