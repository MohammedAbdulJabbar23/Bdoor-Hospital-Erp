package com.albudoor.hms.pharmacy.infrastructure;

import com.albudoor.hms.pharmacy.domain.DrugBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DrugBatchRepository extends JpaRepository<DrugBatch, UUID> {

    /** FEFO order for dispensing: earliest expiry first, only batches with stock and not expired. */
    @Query("""
        SELECT b FROM DrugBatch b
        WHERE b.drugServiceItemId = :drugId
          AND b.qtyRemaining > 0
          AND b.expiryDate >= :today
        ORDER BY b.expiryDate ASC
        """)
    List<DrugBatch> findAvailableForDrug(@Param("drugId") UUID drugId, @Param("today") LocalDate today);

    List<DrugBatch> findAllByDrugServiceItemIdOrderByExpiryDateAsc(UUID drugServiceItemId);

    /** All batches (any drug) expiring on or before {@code limit} that still have stock. */
    @Query("""
        SELECT b FROM DrugBatch b
        WHERE b.qtyRemaining > 0 AND b.expiryDate <= :limit
        ORDER BY b.expiryDate ASC
        """)
    List<DrugBatch> findExpiringBy(@Param("limit") LocalDate limit);
}
