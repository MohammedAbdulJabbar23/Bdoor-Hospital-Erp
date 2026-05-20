package com.albudoor.hms.clinicalcase.patienthistory;

import com.albudoor.hms.clinicalcase.api.DoctorExamResponse;
import com.albudoor.hms.clinicalcase.domain.DoctorExam;
import com.albudoor.hms.clinicalcase.infrastructure.DoctorExamRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PatientHistoryHandler {

    private final VisitRepository visits;
    private final DoctorExamRepository exams;

    public PatientHistoryHandler(VisitRepository visits, DoctorExamRepository exams) {
        this.visits = visits;
        this.exams = exams;
    }

    @Transactional(readOnly = true)
    public PatientHistoryResponse handle(UUID patientId) {
        List<Visit> patientVisits = visits.findAllByPatientIdOrderByStartedAtDesc(patientId);
        if (patientVisits.isEmpty()) {
            // Make 404 vs empty distinguishable: empty array if patient exists but no visits
            // (we don't validate patient here; the caller can 404 separately if needed).
            return new PatientHistoryResponse(patientId, 0, List.of());
        }

        // Fetch exams and index by visitId so we don't N+1
        List<DoctorExam> patientExams = exams.findAllByPatientIdOrderByCreatedAtDesc(patientId);
        Map<UUID, DoctorExam> examByVisit = new HashMap<>();
        for (DoctorExam e : patientExams) examByVisit.put(e.getVisitId(), e);

        List<PatientHistoryResponse.HistoryEntry> entries = patientVisits.stream()
                .map(v -> new PatientHistoryResponse.HistoryEntry(
                        v.getId(), v.getVisitDisplayId(), v.getVisitType(), v.getStatus(),
                        v.getParentVisitId(), v.getOriginatingType(),
                        v.getResultsSummary(),
                        v.getStartedAt(), v.getEndedAt(),
                        examByVisit.containsKey(v.getId())
                                ? DoctorExamResponse.from(examByVisit.get(v.getId()))
                                : null))
                .toList();

        return new PatientHistoryResponse(patientId, entries.size(), entries);
    }
}
