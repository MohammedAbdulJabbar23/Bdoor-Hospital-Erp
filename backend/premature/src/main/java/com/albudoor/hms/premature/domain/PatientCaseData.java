package com.albudoor.hms.premature.domain;

/** Editable Patient Case Form fields (BRD P6); all optional. */
public record PatientCaseData(
        String wardNumber, String nextOfKinAddress, String nextOfKinPhone,
        String treatingSpecialist, String initialDiagnosis, String finalDiagnosis
) {}
