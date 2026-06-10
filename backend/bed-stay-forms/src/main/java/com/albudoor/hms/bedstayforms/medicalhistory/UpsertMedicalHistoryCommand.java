package com.albudoor.hms.bedstayforms.medicalhistory;

import com.albudoor.hms.bedstayforms.domain.MedicalHistoryData;

import java.math.BigDecimal;

/** All fields optional — the paper sheet allows partial completion. */
public record UpsertMedicalHistoryCommand(
        BigDecimal weightKg, BigDecimal heightCm, String doctorName,
        String chiefComplaint, String presentIllnessHx, String psHx, String pmHx,
        String familyHx, String allergicHx,
        String socialSmoker, String socialAlcohol, String socialSleep,
        String drugHx, String physicalExamination
) {
    public MedicalHistoryData toData() {
        return new MedicalHistoryData(weightKg, heightCm, doctorName,
                chiefComplaint, presentIllnessHx, psHx, pmHx, familyHx, allergicHx,
                socialSmoker, socialAlcohol, socialSleep, drugHx, physicalExamination);
    }
}
