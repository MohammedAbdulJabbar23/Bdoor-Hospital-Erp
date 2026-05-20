package com.albudoor.hms.cashier.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cashier payment — one entry in the central cashier queue.
 *
 * <p>Patient identity, visit identity, and the full line-item set are snapshotted at
 * creation so the payment record is durable even if the underlying patient name, visit
 * status, or service-item fee changes later.
 *
 * <p>VIP bypass: when the patient's {@code vip} flag is set, the payment is auto-approved
 * with {@code vipBypass=true} and {@code paymentMethod=VIP_BYPASS}. The audit trail is
 * still written so reporting can quantify VIP-bypassed revenue.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "payment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_display_id", columnNames = "payment_display_id"),
        indexes = {
                @Index(name = "idx_payment_status_created", columnList = "status, created_at"),
                @Index(name = "idx_payment_visit", columnList = "visit_id"),
                @Index(name = "idx_payment_patient", columnList = "patient_id")
        }
)
public class Payment extends AggregateRoot {

    @Id
    private UUID id;

    @Column(name = "payment_display_id", nullable = false, length = 30)
    private String paymentDisplayId;

    // -- Snapshot: visit
    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "visit_display_id", nullable = false, length = 30)
    private String visitDisplayId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", nullable = false, length = 30)
    private VisitType visitType;

    // -- Snapshot: patient
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "patient_mrn", nullable = false, length = 30)
    private String patientMrn;

    @Column(name = "patient_name", nullable = false, length = 300)
    private String patientName;

    @Column(name = "patient_was_vip", nullable = false)
    private boolean patientWasVip;

    // -- Stage / status / amount
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "vip_bypass", nullable = false)
    private boolean vipBypass;

    @Column(name = "total_due", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDue;

    @Column(nullable = false, length = 10)
    private String currency;

    // -- Cashier action
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "cashier_user_id")
    private UUID cashierUserId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "payment_line_item",
            joinColumns = @JoinColumn(name = "payment_id"),
            indexes = @Index(name = "idx_payment_line_payment", columnList = "payment_id")
    )
    @OrderColumn(name = "line_no")
    private List<PaymentLineItem> lineItems = new ArrayList<>();

    public static Payment create(
            String paymentDisplayId,
            UUID visitId, String visitDisplayId, VisitType visitType,
            UUID patientId, String patientMrn, String patientName, boolean patientIsVip,
            PaymentStage stage,
            String currency,
            List<PaymentLineItem> lines
    ) {
        if (paymentDisplayId == null || paymentDisplayId.isBlank()) {
            throw new DomainException("PAYMENT_ID_REQUIRED", "payment display id is required");
        }
        if (visitId == null || patientId == null) {
            throw new DomainException("VISIT_PATIENT_REQUIRED", "visit and patient are required");
        }
        if (stage == null) {
            throw new DomainException("STAGE_REQUIRED", "payment stage is required");
        }
        if (lines == null || lines.isEmpty()) {
            throw new DomainException("PAYMENT_LINES_REQUIRED", "at least one line item is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new DomainException("CURRENCY_REQUIRED", "currency is required");
        }

        Payment p = new Payment();
        p.id = UUID.randomUUID();
        p.paymentDisplayId = paymentDisplayId;
        p.visitId = visitId;
        p.visitDisplayId = visitDisplayId;
        p.visitType = visitType;
        p.patientId = patientId;
        p.patientMrn = patientMrn;
        p.patientName = patientName;
        p.patientWasVip = patientIsVip;
        p.stage = stage;
        p.currency = currency;
        p.lineItems = new ArrayList<>(lines);
        p.totalDue = lines.stream()
                .map(PaymentLineItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // VIP fast-path: auto-approve immediately, audit it.
        if (patientIsVip) {
            p.status = PaymentStatus.APPROVED;
            p.vipBypass = true;
            p.paymentMethod = PaymentMethod.VIP_BYPASS;
            p.decidedAt = Instant.now();
        } else {
            p.status = PaymentStatus.PENDING;
            p.vipBypass = false;
        }

        p.registerEvent(PaymentCreatedEvent.of(p));
        if (p.status == PaymentStatus.APPROVED) {
            p.registerEvent(PaymentApprovedEvent.of(p));
        }
        return p;
    }

    public void approve(PaymentMethod method, UUID cashierUserId) {
        if (this.status != PaymentStatus.PENDING) {
            throw new DomainException("PAYMENT_NOT_PENDING",
                    "Cannot approve a payment in status " + this.status);
        }
        if (method == null || method == PaymentMethod.VIP_BYPASS) {
            throw new DomainException("PAYMENT_METHOD_REQUIRED",
                    "Cashier must pick a real payment method");
        }
        this.status = PaymentStatus.APPROVED;
        this.paymentMethod = method;
        this.cashierUserId = cashierUserId;
        this.decidedAt = Instant.now();
        registerEvent(PaymentApprovedEvent.of(this));
    }

    public void reject(String reason, UUID cashierUserId) {
        if (this.status != PaymentStatus.PENDING) {
            throw new DomainException("PAYMENT_NOT_PENDING",
                    "Cannot reject a payment in status " + this.status);
        }
        this.status = PaymentStatus.REJECTED;
        this.rejectionReason = reason;
        this.cashierUserId = cashierUserId;
        this.decidedAt = Instant.now();
        registerEvent(PaymentRejectedEvent.of(this));
    }
}
