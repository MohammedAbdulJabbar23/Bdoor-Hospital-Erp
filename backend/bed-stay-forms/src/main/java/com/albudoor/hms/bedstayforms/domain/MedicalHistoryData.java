package com.albudoor.hms.bedstayforms.domain;

import java.math.BigDecimal;

/** Editable Medical History Sheet fields (BRD REC-005 §6.6.1), all optional. */
public record MedicalHistoryData(
        BigDecimal weightKg, BigDecimal heightCm, String doctorName,
        String chiefComplaint, String presentIllnessHx, String psHx, String pmHx,
        String familyHx, String allergicHx,
        String socialSmoker, String socialAlcohol, String socialSleep,
        String drugHx, String physicalExamination
) {}
