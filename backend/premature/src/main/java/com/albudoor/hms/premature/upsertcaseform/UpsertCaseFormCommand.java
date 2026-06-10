package com.albudoor.hms.premature.upsertcaseform;

import com.albudoor.hms.premature.domain.PatientCaseData;

public record UpsertCaseFormCommand(
        String wardNumber, String nextOfKinAddress, String nextOfKinPhone,
        String treatingSpecialist, String initialDiagnosis, String finalDiagnosis
) {
    public PatientCaseData toData() {
        return new PatientCaseData(wardNumber, nextOfKinAddress, nextOfKinPhone,
                treatingSpecialist, initialDiagnosis, finalDiagnosis);
    }
}
