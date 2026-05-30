package com.albudoor.hms.premature.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Editable Premature Form fields (BRD §6.5), carried from the upsert command into the aggregate. */
public record PrematureFormData(
        String ageText,
        BigDecimal birthWeightKg, LocalDate birthWeightDate,
        BigDecimal currentWeightKg, LocalDate currentWeightDate,
        Integer gestationalAgeWeeks, Integer gestationalAgeDays,
        Integer correctedGaWeeks, Integer correctedGaDays,
        BigDecimal lengthCm, LocalDate lengthDate,
        BigDecimal ofcCm, LocalDate ofcDate,
        String feedingType,
        BigDecimal kcalPerOz, BigDecimal enteralPerKg, BigDecimal kcalPerKg, BigDecimal gir,
        String pharmacyOthers,
        LocalDate lastCultureDate, String sampleType, String cultureResult,
        String prescriptionNotes, String specialistDoctorNotes
) {}
