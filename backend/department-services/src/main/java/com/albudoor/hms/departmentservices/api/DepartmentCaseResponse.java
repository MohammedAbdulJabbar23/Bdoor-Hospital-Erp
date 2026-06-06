package com.albudoor.hms.departmentservices.api;

import com.albudoor.hms.departmentservices.domain.CaseServiceLine;
import com.albudoor.hms.departmentservices.domain.DepartmentCase;
import com.albudoor.hms.departmentservices.domain.DepartmentCaseStatus;
import com.albudoor.hms.departmentservices.domain.DepartmentCategory;
import com.albudoor.hms.visitmanagement.domain.VisitOrigin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DepartmentCaseResponse(
        UUID id,
        DepartmentCategory category,
        UUID visitId,
        String visitDisplayId,
        VisitOrigin visitOrigin,
        UUID parentVisitId,
        UUID patientId,
        String patientMrn,
        String patientName,
        DepartmentCaseStatus status,
        UUID paymentId,
        Instant finalizedAt,
        String resultsSummary,
        String referralNote,
        Instant createdAt,
        List<Line> services
) {
    public record Line(
            UUID serviceItemId,
            String code,
            String name,
            BigDecimal fee,
            CaseServiceLine.LineStatus status,
            String textFindings,
            BigDecimal numericValue,
            String unit,
            String referenceRange,
            String flag,
            String measurements,
            String bodyRegion,
            String comments,
            String diagnosis,
            Instant uploadedAt,
            UUID uploadedBy
    ) {}

    public static DepartmentCaseResponse from(DepartmentCase c) {
        List<Line> lines = c.getServices().stream()
                .map(s -> new Line(
                        s.getServiceItemId(), s.getServiceCode(), s.getServiceName(), s.getFee(),
                        s.getLineStatus(),
                        s.getTextFindings(), s.getNumericValue(), s.getUnit(), s.getReferenceRange(), s.getFlag(),
                        s.getMeasurements(), s.getBodyRegion(), s.getComments(), s.getDiagnosis(),
                        s.getUploadedAt(), s.getUploadedBy()))
                .toList();
        return new DepartmentCaseResponse(
                c.getId(), c.getCategory(),
                c.getVisitId(), c.getVisitDisplayId(), c.getVisitOrigin(), c.getParentVisitId(),
                c.getPatientId(), c.getPatientMrn(), c.getPatientName(),
                c.getStatus(), c.getPaymentId(),
                c.getFinalizedAt(), c.getResultsSummary(),
                c.getReferralNote(),
                c.getCreatedAt(),
                lines);
    }
}
