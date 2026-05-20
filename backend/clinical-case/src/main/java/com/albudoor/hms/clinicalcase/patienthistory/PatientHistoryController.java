package com.albudoor.hms.clinicalcase.patienthistory;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
@PreAuthorize("isAuthenticated()")
public class PatientHistoryController {

    private final PatientHistoryHandler handler;

    public PatientHistoryController(PatientHistoryHandler handler) {
        this.handler = handler;
    }

    @GetMapping("/{id}/clinical-history")
    public PatientHistoryResponse clinicalHistory(@PathVariable UUID id) {
        return handler.handle(id);
    }
}
