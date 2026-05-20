package com.albudoor.hms.clinicalcase.getexam;

import com.albudoor.hms.clinicalcase.api.DoctorExamResponse;
import com.albudoor.hms.clinicalcase.domain.DoctorExam;
import com.albudoor.hms.clinicalcase.infrastructure.DoctorExamRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/exams")
@PreAuthorize("isAuthenticated()")
public class GetExamController {

    private final DoctorExamRepository repo;

    public GetExamController(DoctorExamRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/by-visit/{visitId}")
    @Transactional(readOnly = true)
    public DoctorExamResponse byVisit(@PathVariable UUID visitId) {
        DoctorExam e = repo.findByVisitId(visitId)
                .orElseThrow(() -> new NotFoundException("No exam for visit: " + visitId));
        return DoctorExamResponse.from(e);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public DoctorExamResponse byId(@PathVariable UUID id) {
        DoctorExam e = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Exam not found: " + id));
        return DoctorExamResponse.from(e);
    }
}
