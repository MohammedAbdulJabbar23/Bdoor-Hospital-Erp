package com.albudoor.hms.premature.api;

import java.math.BigDecimal;
import java.util.List;

public record PrematureCaseResponse(
        AdmissionResponse admission,
        PrematureFormResponse form,
        Prefill prefill,
        List<PrematureTourResponse> tours,
        PatientCaseFormResponse caseForm,
        CaseFilePrefill caseFilePrefill
) {
    public record Prefill(
            String ageText,
            BigDecimal birthWeightKg,
            Integer gestationalAgeWeeks, Integer gestationalAgeDays,
            BigDecimal lengthCm, BigDecimal ofcCm
    ) {}

    /** Registry fields the P6 paper form shows read-only (beyond name/MRN on AdmissionResponse). */
    public record CaseFilePrefill(String motherName, String gender) {}
}
