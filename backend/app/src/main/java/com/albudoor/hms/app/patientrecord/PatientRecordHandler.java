package com.albudoor.hms.app.patientrecord;

import com.albudoor.hms.clinicalcase.patienthistory.PatientHistoryHandler;
import com.albudoor.hms.clinicalcase.patienthistory.PatientHistoryResponse;
import com.albudoor.hms.departmentservices.api.DepartmentCaseResponse;
import com.albudoor.hms.departmentservices.infrastructure.DepartmentCaseRepository;
import com.albudoor.hms.pharmacy.api.DispenseResponse;
import com.albudoor.hms.pharmacy.infrastructure.PharmacyDispenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PatientRecordHandler {

    private final PatientHistoryHandler history;
    private final DepartmentCaseRepository deptCases;
    private final PharmacyDispenseRepository dispenses;

    public PatientRecordHandler(
            PatientHistoryHandler history,
            DepartmentCaseRepository deptCases,
            PharmacyDispenseRepository dispenses
    ) {
        this.history = history;
        this.deptCases = deptCases;
        this.dispenses = dispenses;
    }

    @Transactional(readOnly = true)
    public PatientRecordResponse handle(UUID patientId) {
        PatientHistoryResponse h = history.handle(patientId);

        List<DepartmentCaseResponse> cases = deptCases.findAllByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .map(DepartmentCaseResponse::from)
                .toList();

        List<DispenseResponse> dispensesOut = dispenses.findAllByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .map(DispenseResponse::from)
                .toList();

        return new PatientRecordResponse(
                patientId,
                h.totalVisits(),
                h.entries(),
                cases,
                dispensesOut);
    }
}
