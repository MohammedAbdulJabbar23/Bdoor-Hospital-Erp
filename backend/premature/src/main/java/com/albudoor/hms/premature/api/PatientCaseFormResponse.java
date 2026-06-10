package com.albudoor.hms.premature.api;

import com.albudoor.hms.premature.domain.PatientCaseForm;

public record PatientCaseFormResponse(
        String wardNumber, String nextOfKinAddress, String nextOfKinPhone,
        String treatingSpecialist, String initialDiagnosis, String finalDiagnosis
) {
    public static PatientCaseFormResponse from(PatientCaseForm f) {
        return new PatientCaseFormResponse(f.getWardNumber(), f.getNextOfKinAddress(), f.getNextOfKinPhone(),
                f.getTreatingSpecialist(), f.getInitialDiagnosis(), f.getFinalDiagnosis());
    }
}
