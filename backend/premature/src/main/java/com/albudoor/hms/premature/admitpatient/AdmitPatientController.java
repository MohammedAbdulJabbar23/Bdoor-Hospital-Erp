package com.albudoor.hms.premature.admitpatient;

import com.albudoor.hms.premature.api.AdmissionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/premature/admissions")
public class AdmitPatientController {

    private final AdmitPatientHandler handler;

    public AdmitPatientController(AdmitPatientHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'ADMIN')")
    public ResponseEntity<AdmissionResponse> admit(@Valid @RequestBody AdmitPatientCommand cmd) {
        return ResponseEntity.status(HttpStatus.CREATED).body(AdmissionResponse.from(handler.handle(cmd)));
    }
}
