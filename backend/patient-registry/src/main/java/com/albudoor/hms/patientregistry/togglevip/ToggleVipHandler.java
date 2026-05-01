package com.albudoor.hms.patientregistry.togglevip;

import com.albudoor.hms.patientregistry.domain.Patient;
import com.albudoor.hms.patientregistry.infrastructure.PatientRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ToggleVipHandler {

    private final PatientRepository patients;

    public ToggleVipHandler(PatientRepository patients) {
        this.patients = patients;
    }

    @Transactional
    public Patient handle(UUID patientId, ToggleVipCommand cmd) {
        Patient p = patients.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Patient not found: " + patientId));
        p.setVip(cmd.vip());
        return p;
    }
}
