package com.albudoor.hms.clinicalcase.reopenexam;

import com.albudoor.hms.clinicalcase.api.DoctorExamResponse;
import com.albudoor.hms.clinicalcase.domain.DoctorExam;
import com.albudoor.hms.clinicalcase.infrastructure.DoctorExamRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
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

    public ReopenExamController(DoctorExamRepository repo) {
        this.repo = repo;
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public DoctorExamResponse reopen(@PathVariable UUID id) {
        DoctorExam exam = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Exam not found: " + id));
        exam.reopen();
        return DoctorExamResponse.from(exam);
    }
}
