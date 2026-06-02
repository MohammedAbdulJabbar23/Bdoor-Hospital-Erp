package com.albudoor.hms.clinicalcase.reopenexam;

import com.albudoor.hms.clinicalcase.api.DoctorExamResponse;
import com.albudoor.hms.clinicalcase.domain.DoctorExam;
import com.albudoor.hms.clinicalcase.infrastructure.DoctorExamRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin can re-open a finalized exam back to DRAFT so a doctor can amend findings.
 * Audit trail is preserved via {@code @LastModifiedBy} (the admin who reopened).
 */
@RestController
@RequestMapping("/api/exams")
public class ReopenExamController {

    private final DoctorExamRepository repo;
    private final VisitRepository visits;

    public ReopenExamController(DoctorExamRepository repo, VisitRepository visits) {
        this.repo = repo;
        this.visits = visits;
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public DoctorExamResponse reopen(@PathVariable UUID id) {
        DoctorExam exam = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Exam not found: " + id));

        // Don't reopen an exam whose visit is already closed (COMPLETED/CANCELLED): the
        // resulting DRAFT-on-a-terminal-visit can never be re-finalized (the finalize gate
        // requires the visit to be in progress), leaving the exam permanently stuck.
        Visit visit = visits.findById(exam.getVisitId()).orElse(null);
        if (visit != null && visit.getStatus().isTerminal()) {
            throw new DomainException("VISIT_TERMINAL",
                    "Cannot reopen an exam on a closed visit (" + visit.getStatus() + ")");
        }

        exam.reopen();
        return DoctorExamResponse.from(exam);
    }
}
