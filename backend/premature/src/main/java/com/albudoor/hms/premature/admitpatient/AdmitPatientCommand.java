package com.albudoor.hms.premature.admitpatient;

import com.albudoor.hms.premature.domain.StayUnit;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record AdmitPatientCommand(
        @NotNull UUID visitId,
        @NotNull UUID bedId,
        @Positive int stayValue,
        @NotNull StayUnit stayUnit
) {}
