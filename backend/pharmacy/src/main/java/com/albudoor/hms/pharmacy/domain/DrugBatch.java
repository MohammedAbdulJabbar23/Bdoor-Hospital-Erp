package com.albudoor.hms.pharmacy.domain;

import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Physical batch of a stocked drug. Many batches can exist for the same drug-catalogue-item;
 * dispensing consumes from the earliest-expiring non-empty batch (FEFO).
 */
@Entity
@Table(
    name = "drug_batch",
    indexes = {
        @Index(name = "idx_drug_batch_drug_expiry", columnList = "drug_service_item_id, expiry_date"),
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrugBatch {

    @Id
    private UUID id;

    /**
     * Optimistic-lock token — Hibernate refuses to commit if two concurrent
     * {@code mark-given} runs try to draw from the same batch.
     */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "drug_service_item_id", nullable = false)
    private UUID drugServiceItemId;

    @Column(name = "batch_no", nullable = false, length = 80)
    private String batchNo;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "qty_received", nullable = false)
    private int qtyReceived;

    @Column(name = "qty_remaining", nullable = false)
    private int qtyRemaining;

    @Column(name = "unit_cost")
    private BigDecimal unitCost;

    @Column(length = 200)
    private String supplier;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "received_by")
    private UUID receivedBy;

    public static DrugBatch receive(
            UUID drugServiceItemId,
            String batchNo,
            LocalDate expiryDate,
            int qty,
            BigDecimal unitCost,
            String supplier,
            UUID receivedBy
    ) {
        if (qty <= 0) {
            throw new DomainException("BATCH_QTY_INVALID", "Received quantity must be positive");
        }
        if (expiryDate == null || expiryDate.isBefore(LocalDate.now())) {
            throw new DomainException("BATCH_EXPIRED", "Cannot receive a batch already past expiry");
        }
        DrugBatch b = new DrugBatch();
        b.id = UUID.randomUUID();
        b.drugServiceItemId = drugServiceItemId;
        b.batchNo = batchNo;
        b.expiryDate = expiryDate;
        b.qtyReceived = qty;
        b.qtyRemaining = qty;
        b.unitCost = unitCost;
        b.supplier = supplier;
        b.receivedAt = Instant.now();
        b.receivedBy = receivedBy;
        return b;
    }

    /** Subtract units from this batch's remaining stock. Returns how many were actually drawn. */
    public int draw(int wanted) {
        if (wanted <= 0) return 0;
        int drawn = Math.min(wanted, qtyRemaining);
        qtyRemaining -= drawn;
        return drawn;
    }

    public boolean isExpired() {
        return expiryDate.isBefore(LocalDate.now());
    }

    public boolean isExpiringSoon(int days) {
        return !isExpired() && expiryDate.isBefore(LocalDate.now().plusDays(days));
    }
}
