package com.albudoor.hms.patientregistry.togglevip;

import com.albudoor.hms.patientregistry.registernewpatient.PatientResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
public class ToggleVipController {

    private final ToggleVipHandler handler;

    public ToggleVipController(ToggleVipHandler handler) {
        this.handler = handler;
    }

    @PutMapping("/{id}/vip")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN', 'CASHIER')")
    public PatientResponse toggle(@PathVariable UUID id, @Valid @RequestBody ToggleVipCommand cmd) {
        return PatientResponse.from(handler.handle(id, cmd));
    }
}
