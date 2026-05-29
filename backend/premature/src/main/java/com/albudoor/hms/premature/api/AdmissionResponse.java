package com.albudoor.hms.premature.api;

import com.albudoor.hms.premature.domain.PrematureAdmission;

import java.time.Instant;
import java.util.UUID;

public record AdmissionResponse(
        UUID id, UUID visitId, String visitDisplayId,
        UUID patientId, String patientMrn, String patientName,
        UUID bedId, String bedCode, String status,
        int stayValue, String stayUnit,
        Instant admittedAt, Instant stayExpiresAt,
        Instant treatmentFinishedAt, Instant closedAt,
        UUID initialPaymentId, UUID finalPaymentId
) {
    public static AdmissionResponse from(PrematureAdmission a) {
        return new AdmissionResponse(
                a.getId(), a.getVisitId(), a.getVisitDisplayId(),
                a.getPatientId(), a.getPatientMrn(), a.getPatientName(),
                a.getBedId(), a.getBedCode(), a.getStatus().name(),
                a.getStayValue(), a.getStayUnit().name(),
                a.getAdmittedAt(), a.getStayExpiresAt(),
                a.getTreatmentFinishedAt(), a.getClosedAt(),
                a.getInitialPaymentId(), a.getFinalPaymentId());
    }
}
