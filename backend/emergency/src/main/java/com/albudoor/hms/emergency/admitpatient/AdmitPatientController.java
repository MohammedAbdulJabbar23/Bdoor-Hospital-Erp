package com.albudoor.hms.emergency.admitpatient;

import com.albudoor.hms.emergency.api.CaseResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("emergencyAdmitPatientController")
@RequestMapping("/api/emergency/cases")
public class AdmitPatientController {
    private final AdmitPatientHandler handler;
    public AdmitPatientController(AdmitPatientHandler handler) { this.handler = handler; }

    @PostMapping
    @PreAuthorize("hasAnyRole('EMERGENCY_STAFF', 'ADMIN')")
    public ResponseEntity<CaseResponse> admit(@Valid @RequestBody AdmitPatientCommand cmd) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CaseResponse.from(handler.handle(cmd)));
    }
}
