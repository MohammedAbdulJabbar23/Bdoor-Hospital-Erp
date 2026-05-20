package com.albudoor.hms.patientregistry.archive;

import com.albudoor.hms.patientregistry.domain.Patient;
import com.albudoor.hms.patientregistry.infrastructure.PatientRepository;
import com.albudoor.hms.patientregistry.registernewpatient.PatientResponse;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Patient archive / unarchive — Patient records are immortal (one MRN), but a patient
 * created in error can be soft-deleted via archive(). Addresses HMS punch-list item:
 * "Cancel new patient screen doesn't work."
 */
@RestController
@RequestMapping("/api/patients")
public class ArchivePatientController {

    private final PatientRepository patients;

    public ArchivePatientController(PatientRepository patients) {
        this.patients = patients;
    }

    @PutMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Transactional
    public PatientResponse archive(@PathVariable UUID id) {
        Patient p = patients.findById(id).orElseThrow(() -> new NotFoundException("Patient not found: " + id));
        p.archive();
        return PatientResponse.from(patients.save(p));
    }

    @PutMapping("/{id}/unarchive")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Transactional
    public PatientResponse unarchive(@PathVariable UUID id) {
        Patient p = patients.findById(id).orElseThrow(() -> new NotFoundException("Patient not found: " + id));
        p.unarchive();
        return PatientResponse.from(patients.save(p));
    }
}
