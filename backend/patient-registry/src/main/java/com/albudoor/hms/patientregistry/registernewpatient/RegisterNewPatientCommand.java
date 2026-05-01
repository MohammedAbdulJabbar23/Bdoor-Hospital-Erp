package com.albudoor.hms.patientregistry.registernewpatient;

import com.albudoor.hms.patientregistry.domain.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterNewPatientCommand(
        @NotBlank @Size(max = 300) String fullName,
        @NotNull Gender gender,
        @NotNull @Past LocalDate dateOfBirth,
        @Size(max = 50) String nationalId,
        @Size(max = 30) String mobileNumber,
        @Size(max = 500) String address,
        @Size(max = 200) String occupation,
        @Size(max = 200) String emergencyContactName,
        @Size(max = 30) String emergencyContactMobile,
        boolean vip
) {}
