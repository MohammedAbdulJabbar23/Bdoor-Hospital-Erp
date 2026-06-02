package com.albudoor.hms.cashier.infrastructure;

import com.albudoor.hms.cashier.domain.Payment;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Query("""
            SELECT p FROM Payment p
            WHERE (:status IS NULL OR p.status = :status)
              AND (:stage IS NULL OR p.stage = :stage)
            ORDER BY p.createdAt DESC
            """)
    Page<Payment> search(
            @Param("status") PaymentStatus status,
            @Param("stage") PaymentStage stage,
            Pageable pageable
    );

    List<Payment> findAllByVisitIdOrderByCreatedAtDesc(UUID visitId);

    List<Payment> findAllByPatientIdOrderByCreatedAtDesc(UUID patientId);

    boolean existsByVisitIdAndStageAndStatusIn(
            UUID visitId, PaymentStage stage, List<PaymentStatus> statuses);

    long countByStatus(PaymentStatus status);
}
