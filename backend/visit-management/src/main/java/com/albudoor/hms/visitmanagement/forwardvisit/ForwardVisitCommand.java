package com.albudoor.hms.visitmanagement.forwardvisit;

import com.albudoor.hms.visitmanagement.domain.VisitType;
import jakarta.validation.constraints.NotNull;

public record ForwardVisitCommand(
        @NotNull VisitType targetType
) {}
