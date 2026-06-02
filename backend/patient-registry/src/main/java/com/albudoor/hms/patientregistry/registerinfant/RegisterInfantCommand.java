package com.albudoor.hms.patientregistry.registerinfant;

import com.albudoor.hms.patientregistry.domain.DeliveryType;
import com.albudoor.hms.patientregistry.domain.Gender;
import com.albudoor.hms.patientregistry.domain.PlaceOfBirth;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Infant intake. National ID is intentionally not collected — Iraqi infants register later.
 * Either {@code motherPatientId} (existing patient) or {@code motherName} must be supplied.
 */
public record RegisterInfantCommand(
        @Size(max = 300) String fullName,             // optional; auto-fill "Baby <familyName>" if blank
        @NotNull Gender gender,
        @NotNull @PastOrPresent LocalDate dateOfBirth,
        LocalTime dobTime,

        UUID motherPatientId,
        @Size(max = 200) String motherName,
        @Size(max = 50) String motherNationalId,
        @Size(max = 30) String motherMobile,
        @Size(max = 200) String fatherName,
        @Size(max = 30) String fatherMobile,

        @NotNull PlaceOfBirth placeOfBirth,
        @NotNull DeliveryType deliveryType,
        @Min(0) @Max(10) Integer apgar1Min,
        @Min(0) @Max(10) Integer apgar5Min,
        @Positive BigDecimal birthWeightKg,
        @Positive BigDecimal lengthCm,
        @Positive BigDecimal ofcCm,
        @Min(20) Integer gestationalAgeWeeks,
        @Min(0) Integer gestationalAgeDays,

        @NotBlank @Size(max = 200) String guardianName,
        @Size(max = 100) String guardianRelationship,
        @Size(max = 30) String guardianMobile,
        @Size(max = 50) String guardianNationalId,

        boolean vip
) {}
