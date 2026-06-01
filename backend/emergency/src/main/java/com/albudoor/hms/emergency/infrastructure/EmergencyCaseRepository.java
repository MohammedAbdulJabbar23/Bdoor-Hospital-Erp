package com.albudoor.hms.emergency.infrastructure;

import com.albudoor.hms.emergency.domain.EmergencyCase;
import com.albudoor.hms.emergency.domain.EmergencyCaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmergencyCaseRepository extends JpaRepository<EmergencyCase, UUID> {
    Optional<EmergencyCase> findByVisitId(UUID visitId);
    Optional<EmergencyCase> findByInitialPaymentId(UUID paymentId);
    Optional<EmergencyCase> findByFinalPaymentId(UUID paymentId);
    Optional<EmergencyCase> findByBedIdAndStatusIn(UUID bedId, List<EmergencyCaseStatus> statuses);
    List<EmergencyCase> findAllByStatusInOrderByAdmittedAtDesc(List<EmergencyCaseStatus> statuses);
}
