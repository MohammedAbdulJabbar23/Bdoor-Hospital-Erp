package com.albudoor.hms.app.patientrecord;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
@PreAuthorize("isAuthenticated()")
public class PatientRecordController {

    private final PatientRecordHandler handler;

    public PatientRecordController(PatientRecordHandler handler) {
        this.handler = handler;
    }

    @GetMapping("/{id}/record")
    public PatientRecordResponse record(@PathVariable UUID id) {
        return handler.handle(id);
    }
}
