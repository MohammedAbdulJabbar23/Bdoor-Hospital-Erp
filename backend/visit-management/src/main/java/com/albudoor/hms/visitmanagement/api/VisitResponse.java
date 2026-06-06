package com.albudoor.hms.visitmanagement.api;

import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitOrigin;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.domain.VisitType;

import java.time.Instant;
import java.util.UUID;

public record VisitResponse(
        UUID id,
        String visitDisplayId,
        UUID patientId,
        String patientMrn,
        String patientName,
        VisitType visitType,
        VisitOrigin origin,
        VisitStatus status,
        UUID parentVisitId,
        VisitType originatingType,
        UUID assignedDoctorId,
        Instant startedAt,
        Instant endedAt,
        String closureReason,
        String resultsSummary,
        Instant resultsLastUpdatedAt,
        String referralNote
) {
    public static VisitResponse from(Visit v) {
        return new VisitResponse(
                v.getId(), v.getVisitDisplayId(),
                v.getPatientId(), v.getPatientMrn(), v.getPatientName(),
                v.getVisitType(), v.getOrigin(), v.getStatus(),
                v.getParentVisitId(), v.getOriginatingType(), v.getAssignedDoctorId(),
                v.getStartedAt(), v.getEndedAt(),
                v.getClosureReason(), v.getResultsSummary(), v.getResultsLastUpdatedAt(),
                v.getReferralNote());
    }
}
