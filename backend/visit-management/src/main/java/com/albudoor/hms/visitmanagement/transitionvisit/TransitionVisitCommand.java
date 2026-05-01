package com.albudoor.hms.visitmanagement.transitionvisit;

import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import jakarta.validation.constraints.NotNull;

public record TransitionVisitCommand(
        @NotNull VisitStatus target,
        String reason
) {}
