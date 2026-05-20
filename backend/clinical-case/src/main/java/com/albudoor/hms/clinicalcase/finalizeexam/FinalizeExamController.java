package com.albudoor.hms.clinicalcase.finalizeexam;

import com.albudoor.hms.clinicalcase.api.DoctorExamResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/exams")
public class FinalizeExamController {

    private final FinalizeExamHandler handler;

    public FinalizeExamController(FinalizeExamHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasAnyRole('DOCTOR', 'EMERGENCY_STAFF', 'PREMATURE_STAFF', 'ADMIN')")
    public DoctorExamResponse finalize(@PathVariable UUID id) {
        return DoctorExamResponse.from(handler.handle(id));
    }
}
