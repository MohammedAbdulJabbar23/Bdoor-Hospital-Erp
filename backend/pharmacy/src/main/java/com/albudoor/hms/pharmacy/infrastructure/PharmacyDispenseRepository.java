package com.albudoor.hms.pharmacy.infrastructure;

import com.albudoor.hms.pharmacy.domain.DispenseStatus;
import com.albudoor.hms.pharmacy.domain.PharmacyDispense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PharmacyDispenseRepository extends JpaRepository<PharmacyDispense, UUID> {

    Optional<PharmacyDispense> findByExamId(UUID examId);

    Optional<PharmacyDispense> findByChargePaymentId(UUID paymentId);

    List<PharmacyDispense> findAllByPatientIdOrderByCreatedAtDesc(UUID patientId);

    @Query("""
            SELECT d FROM PharmacyDispense d
            WHERE (:status IS NULL OR d.status = :status)
            ORDER BY d.createdAt DESC
            """)
    Page<PharmacyDispense> search(
            @Param("status") DispenseStatus status,
            Pageable pageable
    );
}
