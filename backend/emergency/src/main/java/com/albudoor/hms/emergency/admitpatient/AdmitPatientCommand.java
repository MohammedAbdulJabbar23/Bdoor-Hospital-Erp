package com.albudoor.hms.emergency.admitpatient;

import com.albudoor.hms.emergency.domain.StayUnit;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record AdmitPatientCommand(
        @NotNull UUID visitId,
        @NotNull UUID bedId,
        @NotNull UUID serviceItemId,
        @Positive int stayValue,
        @NotNull StayUnit stayUnit
) {}
