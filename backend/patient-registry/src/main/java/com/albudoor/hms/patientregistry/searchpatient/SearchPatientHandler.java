package com.albudoor.hms.patientregistry.searchpatient;

import com.albudoor.hms.patientregistry.domain.Patient;
import com.albudoor.hms.patientregistry.infrastructure.PatientRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SearchPatientHandler {

    private final PatientRepository patients;

    public SearchPatientHandler(PatientRepository patients) {
        this.patients = patients;
    }

    @Transactional(readOnly = true)
    public Page<Patient> search(String query, int page, int size) {
        return patients.search(escapeLike(query), PageRequest.of(page, Math.min(size, 100)));
    }

    /**
     * Escape LIKE metacharacters so a query like {@code %} or {@code a_b} is treated as a
     * literal substring, not a wildcard pattern. Backslash is escaped first (it is the ESCAPE
     * char declared in the repository query) so it does not double-escape the others.
     */
    static String escapeLike(String q) {
        if (q == null) return null;
        return q.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    @Transactional(readOnly = true)
    public Patient byId(UUID id) {
        return patients.findById(id)
                .orElseThrow(() -> new NotFoundException("Patient not found: " + id));
    }

    @Transactional(readOnly = true)
    public Patient byMrn(String mrn) {
        return patients.findByMrn(mrn)
                .orElseThrow(() -> new NotFoundException("Patient not found: " + mrn));
    }
}
