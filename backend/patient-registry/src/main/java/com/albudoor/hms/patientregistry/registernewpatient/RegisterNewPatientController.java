package com.albudoor.hms.patientregistry.registernewpatient;

import com.albudoor.hms.patientregistry.domain.Patient;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
public class RegisterNewPatientController {

    private final RegisterNewPatientHandler handler;

    public RegisterNewPatientController(RegisterNewPatientHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<PatientResponse> register(@Valid @RequestBody RegisterNewPatientCommand cmd) {
        Patient saved = handler.handle(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(PatientResponse.from(saved));
    }
}
