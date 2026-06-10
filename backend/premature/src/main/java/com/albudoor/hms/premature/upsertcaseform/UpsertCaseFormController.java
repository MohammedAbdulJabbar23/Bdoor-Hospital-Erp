package com.albudoor.hms.premature.upsertcaseform;

import com.albudoor.hms.premature.api.PatientCaseFormResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/premature/admissions")
public class UpsertCaseFormController {

    private final UpsertCaseFormHandler handler;

    public UpsertCaseFormController(UpsertCaseFormHandler handler) {
        this.handler = handler;
    }

    @PutMapping("/{id}/case-form")
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'DOCTOR', 'ADMIN')")
    public PatientCaseFormResponse upsert(@PathVariable UUID id, @Valid @RequestBody UpsertCaseFormCommand cmd) {
        return PatientCaseFormResponse.from(handler.handle(id, cmd));
    }
}
