package com.albudoor.hms.premature.api;

import com.albudoor.hms.visitmanagement.domain.Visit;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID visitId, String visitDisplayId, String visitType, String status,
        String resultsSummary, Instant startedAt
) {
    public static OrderResponse from(Visit v) {
        return new OrderResponse(v.getId(), v.getVisitDisplayId(), v.getVisitType().name(),
                v.getStatus().name(), v.getResultsSummary(), v.getStartedAt());
    }
}
