package com.albudoor.hms.premature.infrastructure;

import com.albudoor.hms.premature.domain.PrematureForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PrematureFormRepository extends JpaRepository<PrematureForm, UUID> {
    Optional<PrematureForm> findByAdmissionId(UUID admissionId);
}
