package com.albudoor.hms.premature.api;

import java.math.BigDecimal;
import java.util.List;

public record PrematureCaseResponse(
        AdmissionResponse admission,
        PrematureFormResponse form,
        Prefill prefill,
        List<PrematureTourResponse> tours
) {
    public record Prefill(
            String ageText,
            BigDecimal birthWeightKg,
            Integer gestationalAgeWeeks, Integer gestationalAgeDays,
            BigDecimal lengthCm, BigDecimal ofcCm
    ) {}
}
