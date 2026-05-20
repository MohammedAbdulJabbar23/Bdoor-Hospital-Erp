package com.albudoor.hms.pharmacy.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
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
 * Pharmacy dispense — created when a doctor finalizes an exam with prescriptions.
 *
 * <p>The dispense snapshots the patient/visit/doctor identity AND the prescription lines
 * (with their pricing where the drug resolves to a catalogue entry). Once finalized into
 * the pharmacy queue, the prescription record on the exam can later be deleted/edited via
 * an admin reopen and the dispense remains accurate.
 *
 * <p>Lifecycle:
 * <pre>
 *           charge()            (payment approved)         markGiven()
 *  PENDING ─────────► AWAITING_PAYMENT ─────────► READY_TO_GIVE ─────────► DISPENSED
 *      │                    │                           │
 *      └─────────cancel()──┴────────────cancel()────────┘
 *                                                        ▲
 *                                  (payment rejected)    │
 *                                                        ▼
 *                                                    PENDING (re-chargeable)
 * </pre>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "pharmacy_dispense",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_pharmacy_dispense_display", columnNames = "dispense_display_id"),
                @UniqueConstraint(name = "uk_pharmacy_dispense_exam",    columnNames = "exam_id")
        },
        indexes = {
                @Index(name = "idx_pharmacy_dispense_status_created", columnList = "status, created_at"),
                @Index(name = "idx_pharmacy_dispense_patient",        columnList = "patient_id"),
                @Index(name = "idx_pharmacy_dispense_visit",          columnList = "visit_id"),
                @Index(name = "idx_pharmacy_dispense_payment",        columnList = "charge_payment_id")
        }
)
public class PharmacyDispense extends AggregateRoot {

    @Id
    private UUID id;

    @Column(name = "dispense_display_id", nullable = false, length = 30)
    private String dispenseDisplayId;

    @Column(name = "exam_id")
    private UUID examId;

    @Column(name = "visit_id")
    private UUID visitId;

    @Column(name = "visit_display_id", length = 30)
    private String visitDisplayId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "patient_mrn", nullable = false, length = 30)
    private String patientMrn;

    @Column(name = "patient_name", nullable = false, length = 300)
    private String patientName;

    @Column(name = "doctor_id")
    private UUID doctorId;

    @Column(name = "doctor_name", length = 200)
    private String doctorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DispenseStatus status;

    @Column(name = "charge_payment_id")
    private UUID chargePaymentId;

    @Column(name = "charged_at")
    private Instant chargedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "given_at")
    private Instant givenAt;

    @Column(name = "given_by_user_id")
    private UUID givenByUserId;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_reason", length = 500)
    private String cancelledReason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "pharmacy_dispense_line",
            joinColumns = @JoinColumn(name = "dispense_id")
    )
    @OrderColumn(name = "line_no")
    private List<DispenseLine> lines = new ArrayList<>();

    public static PharmacyDispense fromExam(
            String dispenseDisplayId,
            UUID examId,
            UUID visitId, String visitDisplayId,
            UUID patientId, String patientMrn, String patientName,
            UUID doctorId, String doctorName,
            List<DispenseLine> lines
    ) {
        if (lines == null || lines.isEmpty()) {
            throw new DomainException("DISPENSE_LINES_REQUIRED",
                    "Cannot create a pharmacy dispense without prescription lines");
        }
        PharmacyDispense d = new PharmacyDispense();
        d.id = UUID.randomUUID();
        d.dispenseDisplayId = dispenseDisplayId;
        d.examId = examId;
        d.visitId = visitId;
        d.visitDisplayId = visitDisplayId;
        d.patientId = patientId;
        d.patientMrn = patientMrn;
        d.patientName = patientName;
        d.doctorId = doctorId;
        d.doctorName = doctorName;
        d.status = DispenseStatus.PENDING;
        d.lines.addAll(lines);
        int billable = (int) lines.stream().filter(DispenseLine::isBillable).count();
        d.registerEvent(DispenseCreatedEvent.of(d, billable));
        return d;
    }

    /**
     * Walk-in OTC sale — patient brings drugs to the counter; no exam, no prescribing doctor.
     * A PHARMACY-type visit is created upstream to anchor the payment in the central cashier.
     */
    public static PharmacyDispense otcSale(
            String dispenseDisplayId,
            UUID visitId, String visitDisplayId,
            UUID patientId, String patientMrn, String patientName,
            List<DispenseLine> lines
    ) {
        if (lines == null || lines.isEmpty()) {
            throw new DomainException("DISPENSE_LINES_REQUIRED",
                    "Cannot create an OTC sale without drug lines");
        }
        if (lines.stream().anyMatch(l -> l.getDrugServiceItemId() == null)) {
            throw new DomainException("OTC_REQUIRES_CATALOGUE_DRUGS",
                    "OTC sale lines must reference catalogue drugs (no free-text)");
        }
        PharmacyDispense d = new PharmacyDispense();
        d.id = UUID.randomUUID();
        d.dispenseDisplayId = dispenseDisplayId;
        d.examId = null;
        d.visitId = visitId;
        d.visitDisplayId = visitDisplayId;
        d.patientId = patientId;
        d.patientMrn = patientMrn;
        d.patientName = patientName;
        d.doctorId = null;
        d.doctorName = null;
        d.status = DispenseStatus.PENDING;
        d.lines.addAll(lines);
        int billable = (int) lines.stream().filter(DispenseLine::isBillable).count();
        d.registerEvent(DispenseCreatedEvent.of(d, billable));
        return d;
    }

    /** Cashier handoff: links the created payment and parks the dispense waiting for cashier approval. */
    public void charge(UUID paymentId) {
        if (status != DispenseStatus.PENDING) {
            throw new DomainException("DISPENSE_NOT_CHARGEABLE",
                    "Cannot charge a dispense in status " + status);
        }
        if (paymentId == null) {
            throw new DomainException("PAYMENT_REQUIRED",
                    "A payment must be supplied when charging a dispense");
        }
        this.chargePaymentId = paymentId;
        this.chargedAt = Instant.now();
        this.status = DispenseStatus.AWAITING_PAYMENT;
    }

    /** Bridge moves the dispense forward when the linked payment is approved. */
    public void onPaymentApproved() {
        if (status != DispenseStatus.AWAITING_PAYMENT) return;
        this.paidAt = Instant.now();
        this.status = DispenseStatus.READY_TO_GIVE;
    }

    /** Bridge reverts the dispense when the linked payment is rejected — pharmacist can re-charge or cancel. */
    public void onPaymentRejected() {
        if (status != DispenseStatus.AWAITING_PAYMENT) return;
        this.status = DispenseStatus.PENDING;
        this.chargePaymentId = null;
        this.chargedAt = null;
    }

    public void markGiven(UUID userId) {
        if (status != DispenseStatus.READY_TO_GIVE) {
            throw new DomainException("DISPENSE_NOT_READY",
                    "Cannot mark given a dispense in status " + status);
        }
        this.status = DispenseStatus.DISPENSED;
        this.givenAt = Instant.now();
        this.givenByUserId = userId;
        registerEvent(DispenseGivenEvent.of(this));
    }

    public void cancel(String reason) {
        if (status == DispenseStatus.DISPENSED || status == DispenseStatus.CANCELLED) {
            throw new DomainException("DISPENSE_ALREADY_TERMINAL",
                    "Cannot cancel a dispense in status " + status);
        }
        this.status = DispenseStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        this.cancelledReason = reason;
    }

    public BigDecimal billableTotal() {
        return lines.stream()
                .map(DispenseLine::getLineTotal)
                .filter(t -> t != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
