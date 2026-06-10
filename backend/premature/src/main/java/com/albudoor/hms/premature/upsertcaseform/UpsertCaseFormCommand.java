package com.albudoor.hms.premature.upsertcaseform;

import com.albudoor.hms.premature.domain.PatientCaseData;
import jakarta.validation.constraints.Size;

public record UpsertCaseFormCommand(
        @Size(max = 60) String wardNumber,
        @Size(max = 500) String nextOfKinAddress,
        @Size(max = 60) String nextOfKinPhone,
        @Size(max = 200) String treatingSpecialist,
        @Size(max = 2000) String initialDiagnosis,
        @Size(max = 2000) String finalDiagnosis
) {
    public PatientCaseData toData() {
        return new PatientCaseData(wardNumber, nextOfKinAddress, nextOfKinPhone,
                treatingSpecialist, initialDiagnosis, finalDiagnosis);
    }
}
