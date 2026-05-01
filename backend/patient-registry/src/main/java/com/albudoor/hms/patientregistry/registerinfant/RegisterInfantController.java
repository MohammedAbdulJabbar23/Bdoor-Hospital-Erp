package com.albudoor.hms.patientregistry.registerinfant;

import com.albudoor.hms.patientregistry.domain.Patient;
import com.albudoor.hms.patientregistry.registernewpatient.PatientResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients/infants")
public class RegisterInfantController {

    private final RegisterInfantHandler handler;

    public RegisterInfantController(RegisterInfantHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN', 'PREMATURE_STAFF')")
    public ResponseEntity<PatientResponse> register(@Valid @RequestBody RegisterInfantCommand cmd) {
        Patient saved = handler.handle(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(PatientResponse.from(saved));
    }
}
