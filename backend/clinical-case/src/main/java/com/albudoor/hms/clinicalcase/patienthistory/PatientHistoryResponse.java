package com.albudoor.hms.clinicalcase.patienthistory;

import com.albudoor.hms.clinicalcase.api.DoctorExamResponse;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.domain.VisitType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PatientHistoryResponse(
        UUID patientId,
        int totalVisits,
        List<HistoryEntry> entries,
        /** Unified cross-module timeline (visits, exams, admissions, forms, documents), newest first. */
        List<com.albudoor.hms.clinicalcase.history.HistoryEntry> timeline
) {
    public record HistoryEntry(
            UUID visitId,
            String visitDisplayId,
            VisitType visitType,
            VisitStatus status,
            UUID parentVisitId,
            VisitType originatingType,
            String resultsSummary,
            Instant startedAt,
            Instant endedAt,
            DoctorExamResponse exam
    ) {}
}
