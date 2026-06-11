package com.albudoor.hms.clinicalcase.patienthistory;

import com.albudoor.hms.clinicalcase.api.DoctorExamResponse;
import com.albudoor.hms.clinicalcase.domain.DiagnosisEntry;
import com.albudoor.hms.clinicalcase.domain.DoctorExam;
import com.albudoor.hms.clinicalcase.history.HistoryContributor;
import com.albudoor.hms.clinicalcase.history.HistoryEntry;
import com.albudoor.hms.clinicalcase.history.HistoryEntryType;
import com.albudoor.hms.clinicalcase.history.HistoryRefs;
import com.albudoor.hms.clinicalcase.infrastructure.DoctorExamRepository;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PatientHistoryHandler {

    private final VisitRepository visits;
    private final DoctorExamRepository exams;
    private final List<HistoryContributor> contributors;

    public PatientHistoryHandler(VisitRepository visits, DoctorExamRepository exams,
                                 List<HistoryContributor> contributors) {
        this.visits = visits;
        this.exams = exams;
        this.contributors = contributors;
    }

    @Transactional(readOnly = true)
    public PatientHistoryResponse handle(UUID patientId) {
        List<Visit> patientVisits = visits.findAllByPatientIdOrderByStartedAtDesc(patientId);
        List<DoctorExam> patientExams = exams.findAllByPatientIdOrderByCreatedAtDesc(patientId);

        List<HistoryEntry> timeline = buildTimeline(patientId, patientVisits, patientExams);

        if (patientVisits.isEmpty()) {
            // Make 404 vs empty distinguishable: empty array if patient exists but no visits
            // (we don't validate patient here; the caller can 404 separately if needed).
            return new PatientHistoryResponse(patientId, 0, List.of(), timeline);
        }

        // Index exams by visitId so we don't N+1
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

        return new PatientHistoryResponse(patientId, entries.size(), entries, timeline);
    }

    private List<HistoryEntry> buildTimeline(UUID patientId, List<Visit> patientVisits, List<DoctorExam> patientExams) {
        List<HistoryEntry> timeline = new ArrayList<>();
        for (HistoryContributor c : contributors) timeline.addAll(c.contribute(patientId));
        // clinical-case's own contributions: visits + finalized exams
        for (Visit v : patientVisits) {
            timeline.add(new HistoryEntry(v.getStartedAt(), HistoryEntryType.VISIT,
                    v.getVisitType().name(), v.getVisitType().name() + " visit " + v.getVisitDisplayId(),
                    v.getResultsSummary(), HistoryRefs.visit(v.getId())));
        }
        for (DoctorExam e : patientExams) {
            if (e.getFinalizedAt() != null) {
                timeline.add(new HistoryEntry(e.getFinalizedAt(), HistoryEntryType.EXAM, "CLINICAL",
                        "Doctor exam finalized", diagnosisSummary(e), HistoryRefs.visit(e.getVisitId())));
            }
        }
        timeline.sort(Comparator.comparing(HistoryEntry::at).reversed());
        return timeline;
    }

    private static String diagnosisSummary(DoctorExam e) {
        if (e.getDiagnoses() == null || e.getDiagnoses().isEmpty()) return null;
        String joined = e.getDiagnoses().stream()
                .map(DiagnosisEntry::getDescription)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
        return joined.isEmpty() ? null : joined;
    }
}
