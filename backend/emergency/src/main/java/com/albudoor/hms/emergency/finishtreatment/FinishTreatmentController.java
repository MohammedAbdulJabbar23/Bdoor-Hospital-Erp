package com.albudoor.hms.emergency.finishtreatment;

import com.albudoor.hms.emergency.api.CaseResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController("emergencyFinishTreatmentController")
@RequestMapping("/api/emergency/cases")
public class FinishTreatmentController {

    private final FinishTreatmentHandler handler;

    public FinishTreatmentController(FinishTreatmentHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/finish-treatment")
    @PreAuthorize("hasAnyRole('EMERGENCY_STAFF', 'DOCTOR', 'ADMIN')")
    public CaseResponse finish(@PathVariable UUID id,
                               @RequestBody(required = false) FinishTreatmentCommand cmd) {
        return CaseResponse.from(handler.handle(id,
                cmd != null ? cmd : new FinishTreatmentCommand(false, null)));
    }
}
