package com.albudoor.hms.visitmanagement.forwardvisit;

import com.albudoor.hms.visitmanagement.domain.VisitType;
import jakarta.validation.constraints.NotNull;

public record ForwardVisitCommand(
        @NotNull VisitType targetType,
        String note
) {
    /** Convenience for callers (e.g. the doctor pause-and-wait flow) that carry no referral note. */
    public ForwardVisitCommand(VisitType targetType) {
        this(targetType, null);
    }
}
