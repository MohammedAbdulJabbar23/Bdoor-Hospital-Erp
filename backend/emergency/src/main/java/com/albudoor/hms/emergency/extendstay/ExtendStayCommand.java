package com.albudoor.hms.emergency.extendstay;

import com.albudoor.hms.emergency.domain.StayUnit;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ExtendStayCommand(
        @Positive int value,
        @NotNull StayUnit unit
) {}
