package com.albudoor.hms.premature.infrastructure;

import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrematureAdmissionRepository extends JpaRepository<PrematureAdmission, UUID> {

    Optional<PrematureAdmission> findByVisitId(UUID visitId);

    Optional<PrematureAdmission> findByInitialPaymentId(UUID paymentId);

    Optional<PrematureAdmission> findByFinalPaymentId(UUID paymentId);

    Optional<PrematureAdmission> findByBedIdAndStatusIn(UUID bedId, List<AdmissionStatus> statuses);

    List<PrematureAdmission> findAllByStatusInOrderByAdmittedAtDesc(List<AdmissionStatus> statuses);

    boolean existsByVisitIdAndStatusIn(UUID visitId, List<AdmissionStatus> statuses);

    long countByStatusInAndStayExpiresAtBefore(List<AdmissionStatus> statuses, Instant threshold);
}
