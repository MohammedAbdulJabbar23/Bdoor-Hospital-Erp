package com.albudoor.hms.patientregistry.registernewpatient;

import com.albudoor.hms.patientregistry.domain.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterNewPatientCommand(
        @NotBlank @Size(max = 300) String fullName,
        @NotNull Gender gender,
        @NotNull @Past LocalDate dateOfBirth,
        @Size(max = 50) String nationalId,
        // Lenient phone shape: digits, plus, dashes, parens and spaces, 6–20 chars. Accepts
        // the test/e2e data (10-digit "077…") while rejecting clearly-bogus values.
        @Size(max = 30) @Pattern(regexp = MOBILE_PATTERN, message = "must be a valid phone number")
        String mobileNumber,
        @Size(max = 500) String address,
        @Size(max = 200) String occupation,
        @Size(max = 200) String emergencyContactName,
        @Size(max = 30) @Pattern(regexp = MOBILE_PATTERN, message = "must be a valid phone number")
        String emergencyContactMobile,
        boolean vip
) {
    /**
     * Shared lenient phone format. {@code @Pattern} skips null but NOT empty strings, and the
     * UI submits unfilled optional fields as "" — so an empty value is explicitly allowed.
     * Accepts the test/e2e data ("077…" 10 digits) and rejects clearly-bogus values.
     */
    static final String MOBILE_PATTERN = "^$|^[0-9+\\-() ]{6,20}$";
}
