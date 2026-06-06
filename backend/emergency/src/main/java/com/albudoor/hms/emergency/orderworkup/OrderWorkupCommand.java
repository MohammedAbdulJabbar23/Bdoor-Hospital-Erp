package com.albudoor.hms.emergency.orderworkup;

import com.albudoor.hms.visitmanagement.domain.VisitType;
import jakarta.validation.constraints.NotNull;

public record OrderWorkupCommand(@NotNull VisitType targetType, String note) {}
