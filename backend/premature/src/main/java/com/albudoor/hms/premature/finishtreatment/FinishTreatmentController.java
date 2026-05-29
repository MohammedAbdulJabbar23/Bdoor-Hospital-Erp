package com.albudoor.hms.premature.finishtreatment;

import com.albudoor.hms.premature.api.AdmissionResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/premature/admissions")
public class FinishTreatmentController {

    private final FinishTreatmentHandler handler;

    public FinishTreatmentController(FinishTreatmentHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/finish-treatment")
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'DOCTOR', 'ADMIN')")
    public AdmissionResponse finish(@PathVariable UUID id) {
        return AdmissionResponse.from(handler.handle(id));
    }
}
