package com.albudoor.hms.clinicalcase.upsertexam;

import com.albudoor.hms.clinicalcase.api.DoctorExamResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exams")
public class UpsertExamController {

    private final UpsertExamHandler handler;

    public UpsertExamController(UpsertExamHandler handler) {
        this.handler = handler;
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'EMERGENCY_STAFF', 'PREMATURE_STAFF', 'ADMIN')")
    public DoctorExamResponse upsert(@Valid @RequestBody UpsertExamCommand cmd) {
        return DoctorExamResponse.from(handler.handle(cmd));
    }
}
