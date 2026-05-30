package com.albudoor.hms.premature.upsertform;

import com.albudoor.hms.premature.domain.PrematureFormData;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpsertFormCommand(
        @NotBlank String ageText,
        @NotNull @PositiveOrZero BigDecimal birthWeightKg, LocalDate birthWeightDate,
        @NotNull @PositiveOrZero BigDecimal currentWeightKg, LocalDate currentWeightDate,
        @NotNull Integer gestationalAgeWeeks, @NotNull Integer gestationalAgeDays,
        @NotNull Integer correctedGaWeeks, @NotNull Integer correctedGaDays,
        @NotNull @PositiveOrZero BigDecimal lengthCm, LocalDate lengthDate,
        @NotNull @PositiveOrZero BigDecimal ofcCm, LocalDate ofcDate,
        @NotBlank String feedingType,
        BigDecimal kcalPerOz, BigDecimal enteralPerKg, BigDecimal kcalPerKg, BigDecimal gir,
        String pharmacyOthers,
        LocalDate lastCultureDate, String sampleType, String cultureResult,
        String prescriptionNotes, String specialistDoctorNotes
) {
    public PrematureFormData toData() {
        return new PrematureFormData(ageText, birthWeightKg, birthWeightDate, currentWeightKg, currentWeightDate,
                gestationalAgeWeeks, gestationalAgeDays, correctedGaWeeks, correctedGaDays,
                lengthCm, lengthDate, ofcCm, ofcDate, feedingType, kcalPerOz, enteralPerKg, kcalPerKg, gir,
                pharmacyOthers, lastCultureDate, sampleType, cultureResult, prescriptionNotes, specialistDoctorNotes);
    }
}
