package com.albudoor.hms.cashier.infrastructure;

import com.albudoor.hms.cashier.domain.Payment;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Queue search. {@code q} is an optional case-insensitive substring matched against
     * patient name / MRN / payment display id / visit display id. The caller is responsible
     * for escaping LIKE wildcards ({@code % _ \}) and passing {@code null} when blank; the
     * SQL uses {@code ESCAPE '\'} so the escaped pattern is taken literally.
     */
    @Query("""
            SELECT p FROM Payment p
            WHERE (:status IS NULL OR p.status = :status)
              AND (:stage IS NULL OR p.stage = :stage)
              AND (:q IS NULL
                   OR LOWER(p.patientName)       LIKE :q ESCAPE '\\'
                   OR LOWER(p.patientMrn)        LIKE :q ESCAPE '\\'
                   OR LOWER(p.paymentDisplayId)  LIKE :q ESCAPE '\\'
                   OR LOWER(p.visitDisplayId)    LIKE :q ESCAPE '\\')
            ORDER BY p.createdAt DESC
            """)
    Page<Payment> search(
            @Param("status") PaymentStatus status,
            @Param("stage") PaymentStage stage,
            @Param("q") String q,
            Pageable pageable
    );

    List<Payment> findAllByVisitIdOrderByCreatedAtDesc(UUID visitId);

    List<Payment> findAllByPatientIdOrderByCreatedAtDesc(UUID patientId);

    boolean existsByVisitIdAndStageAndStatusIn(
            UUID visitId, PaymentStage stage, List<PaymentStatus> statuses);

    long countByStatus(PaymentStatus status);

    // -------- Summary aggregates (uncapped; computed in the DB, not by paging+summing) --------

    /** Sum of {@code totalDue} across all PENDING payments. {@code null} when none → callers coalesce. */
    @Query("SELECT COALESCE(SUM(p.totalDue), 0) FROM Payment p WHERE p.status = :status")
    BigDecimal sumTotalDueByStatus(@Param("status") PaymentStatus status);

    /** Pending counts per stage, as {@code [stage, count]} rows — cheap one-shot for the queue tiles. */
    @Query("""
            SELECT p.stage, COUNT(p) FROM Payment p
            WHERE p.status = :status
            GROUP BY p.stage
            """)
    List<Object[]> countByStatusGroupedByStage(@Param("status") PaymentStatus status);

    /**
     * Count of payments APPROVED within {@code [from, to)} (by {@code decidedAt}),
     * optionally excluding VIP-bypass approvals.
     */
    @Query("""
            SELECT COUNT(p) FROM Payment p
            WHERE p.status = com.albudoor.hms.cashier.domain.PaymentStatus.APPROVED
              AND p.decidedAt >= :from AND p.decidedAt < :to
              AND (:excludeVip = FALSE OR p.vipBypass = FALSE)
            """)
    long countApprovedBetween(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excludeVip") boolean excludeVip
    );

    /**
     * Sum of {@code totalDue} of payments APPROVED within {@code [from, to)} (by {@code decidedAt}),
     * optionally excluding VIP-bypass approvals (no cash actually collected for those).
     */
    @Query("""
            SELECT COALESCE(SUM(p.totalDue), 0) FROM Payment p
            WHERE p.status = com.albudoor.hms.cashier.domain.PaymentStatus.APPROVED
              AND p.decidedAt >= :from AND p.decidedAt < :to
              AND (:excludeVip = FALSE OR p.vipBypass = FALSE)
            """)
    BigDecimal sumApprovedBetween(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excludeVip") boolean excludeVip
    );

    // -------- Reconciliation / close-of-day groupings --------

    /** APPROVED totals within {@code [from, to)} grouped by payment method: {@code [method, sum, count]}. */
    @Query("""
            SELECT p.paymentMethod, COALESCE(SUM(p.totalDue), 0), COUNT(p) FROM Payment p
            WHERE p.status = com.albudoor.hms.cashier.domain.PaymentStatus.APPROVED
              AND p.decidedAt >= :from AND p.decidedAt < :to
            GROUP BY p.paymentMethod
            """)
    List<Object[]> reconcileByMethodBetween(
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /** APPROVED totals within {@code [from, to)} grouped by stage: {@code [stage, sum, count]}. */
    @Query("""
            SELECT p.stage, COALESCE(SUM(p.totalDue), 0), COUNT(p) FROM Payment p
            WHERE p.status = com.albudoor.hms.cashier.domain.PaymentStatus.APPROVED
              AND p.decidedAt >= :from AND p.decidedAt < :to
            GROUP BY p.stage
            """)
    List<Object[]> reconcileByStageBetween(
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
