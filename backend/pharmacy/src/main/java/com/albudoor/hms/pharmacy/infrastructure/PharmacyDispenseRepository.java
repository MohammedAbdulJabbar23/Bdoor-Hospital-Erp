package com.albudoor.hms.pharmacy.infrastructure;

import com.albudoor.hms.pharmacy.domain.DispenseStatus;
import com.albudoor.hms.pharmacy.domain.PharmacyDispense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /**
     * Per-status dispense counts across the whole table, computed by the DB via GROUP BY — never by
     * paging+summing — so the pharmacy-queue KPI tiles are correct regardless of how many dispenses
     * exist (the row listing remains capped). Each row is {@code [DispenseStatus status, Long count]}.
     */
    @Query("""
            SELECT d.status, COUNT(d) FROM PharmacyDispense d
            GROUP BY d.status
            """)
    List<Object[]> countByStatusGrouped();

    /**
     * Number of dispenses handed over (status DISPENSED, by {@code givenAt}) in the given window.
     * Drives the "Dispensed today" KPI — counting only dispenses actually given today, not every
     * DISPENSED dispense ever.
     */
    @Query("""
            SELECT COUNT(d) FROM PharmacyDispense d
            WHERE d.status = com.albudoor.hms.pharmacy.domain.DispenseStatus.DISPENSED
              AND d.givenAt >= :from AND d.givenAt < :to
            """)
    long countDispensedBetween(
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * Committed (reserved) quantity for a drug = sum of dispense-line quantities for that drug
     * across dispenses currently IN-FLIGHT but not yet handed over — i.e. charged and parked in
     * the cashier queue ({@code AWAITING_PAYMENT}) or paid and waiting at the counter
     * ({@code READY_TO_GIVE}). These quantities have not yet decremented stock (that happens at
     * mark-given) but are promised to a patient, so a concurrent charge must not re-promise them.
     *
     * <p>PENDING dispenses are excluded (not yet charged), and DISPENSED/CANCELLED are excluded
     * (terminal — DISPENSED already drew its stock, CANCELLED naturally released). The dispense
     * being charged is excluded by id so it does not count its own quantity against itself.
     */
    @Query("""
            SELECT COALESCE(SUM(line.quantity), 0)
            FROM PharmacyDispense d
            JOIN d.lines line
            WHERE line.drugServiceItemId = :drugId
              AND d.id <> :excludeDispenseId
              AND d.status IN (
                  com.albudoor.hms.pharmacy.domain.DispenseStatus.AWAITING_PAYMENT,
                  com.albudoor.hms.pharmacy.domain.DispenseStatus.READY_TO_GIVE)
            """)
    long committedQtyForDrugExcluding(
            @Param("drugId") UUID drugId,
            @Param("excludeDispenseId") UUID excludeDispenseId
    );
}
