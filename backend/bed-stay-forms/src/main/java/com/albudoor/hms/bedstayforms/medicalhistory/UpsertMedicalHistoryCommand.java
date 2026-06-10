package com.albudoor.hms.bedstayforms.medicalhistory;

import com.albudoor.hms.bedstayforms.domain.MedicalHistoryData;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** All fields optional — the paper sheet allows partial completion. */
public record UpsertMedicalHistoryCommand(
        BigDecimal weightKg, BigDecimal heightCm, @Size(max = 200) String doctorName,
        @Size(max = 2000) String chiefComplaint, @Size(max = 4000) String presentIllnessHx, @Size(max = 2000) String psHx, @Size(max = 2000) String pmHx,
        @Size(max = 2000) String familyHx, @Size(max = 2000) String allergicHx,
        @Size(max = 200) String socialSmoker, @Size(max = 200) String socialAlcohol, @Size(max = 200) String socialSleep,
        @Size(max = 2000) String drugHx, @Size(max = 4000) String physicalExamination
) {
    public MedicalHistoryData toData() {
        return new MedicalHistoryData(weightKg, heightCm, doctorName,
                chiefComplaint, presentIllnessHx, psHx, pmHx, familyHx, allergicHx,
                socialSmoker, socialAlcohol, socialSleep, drugHx, physicalExamination);
    }
}
