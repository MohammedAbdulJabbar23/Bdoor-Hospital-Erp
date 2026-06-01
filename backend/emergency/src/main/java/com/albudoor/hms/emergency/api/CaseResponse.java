package com.albudoor.hms.emergency.api;

import com.albudoor.hms.emergency.domain.EmergencyCase;

import java.time.Instant;
import java.util.UUID;

public record CaseResponse(
        UUID id, UUID visitId, String visitDisplayId,
        UUID patientId, String patientMrn, String patientName,
        UUID bedId, String bedCode,
        UUID serviceItemId, String serviceCode, String serviceName,
        String status, int stayValue, String stayUnit,
        Instant admittedAt, Instant stayExpiresAt, Instant treatmentFinishedAt, Instant closedAt,
        UUID initialPaymentId, UUID finalPaymentId
) {
    public static CaseResponse from(EmergencyCase c) {
        return new CaseResponse(c.getId(), c.getVisitId(), c.getVisitDisplayId(),
                c.getPatientId(), c.getPatientMrn(), c.getPatientName(),
                c.getBedId(), c.getBedCode(),
                c.getServiceItemId(), c.getServiceCode(), c.getServiceName(),
                c.getStatus().name(), c.getStayValue(), c.getStayUnit().name(),
                c.getAdmittedAt(), c.getStayExpiresAt(), c.getTreatmentFinishedAt(), c.getClosedAt(),
                c.getInitialPaymentId(), c.getFinalPaymentId());
    }
}
