package com.albudoor.hms.premature.infrastructure;

import com.albudoor.hms.premature.domain.PatientCaseForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientCaseFormRepository extends JpaRepository<PatientCaseForm, UUID> {
    Optional<PatientCaseForm> findByAdmissionId(UUID admissionId);
}
