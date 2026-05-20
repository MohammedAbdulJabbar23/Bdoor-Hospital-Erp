package com.albudoor.hms.doctorappointment.createdoctor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateDoctorCommand(
        UUID userId,
        @NotBlank @Size(max = 200) String fullName,
        @Size(max = 200) String specialty,
        @PositiveOrZero BigDecimal consultationFee,
        @Size(max = 10) String currency,
        @Size(max = 30) String phone
) {}
