package com.albudoor.hms.app.patientrecord;

import com.albudoor.hms.clinicalcase.patienthistory.PatientHistoryResponse;
import com.albudoor.hms.departmentservices.api.DepartmentCaseResponse;
import com.albudoor.hms.pharmacy.api.DispenseResponse;

import java.util.List;
import java.util.UUID;

/**
 * Aggregated patient record served at {@code GET /api/patients/{id}/record}.
 * Combines clinical history (visits + exams), department cases (lab/radiology/eco/emergency)
 * with line-level findings, and pharmacy dispenses with their drug lines.
 *
 * <p>The composition lives in the {@code app} module because it crosses module
 * boundaries — clinical-case has no awareness of pharmacy or department-services
 * by design.
 */
public record PatientRecordResponse(
        UUID patientId,
        int totalVisits,
        List<PatientHistoryResponse.HistoryEntry> visits,
        List<DepartmentCaseResponse> departmentCases,
        List<DispenseResponse> pharmacyDispenses
) {}
