package com.albudoor.hms.premature.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "prem_admission")
public class PrematureAdmission extends AggregateRoot {

    @Id
    private UUID id;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "visit_display_id", nullable = false, length = 30)
    private String visitDisplayId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "patient_mrn", nullable = false, length = 30)
    private String patientMrn;

    @Column(name = "patient_name", nullable = false, length = 300)
    private String patientName;

    @Column(name = "bed_id", nullable = false)
    private UUID bedId;

    @Column(name = "bed_code", nullable = false, length = 30)
    private String bedCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdmissionStatus status;

    @Column(name = "stay_value", nullable = false)
    private int stayValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "stay_unit", nullable = false, length = 10)
    private StayUnit stayUnit;

    @Column(name = "admitted_at", nullable = false)
    private Instant admittedAt;

    @Column(name = "stay_expires_at", nullable = false)
    private Instant stayExpiresAt;

    @Column(name = "treatment_finished_at")
    private Instant treatmentFinishedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "initial_payment_id")
    private UUID initialPaymentId;

    @Column(name = "final_payment_id")
    private UUID finalPaymentId;

    public static PrematureAdmission open(
            UUID visitId, String visitDisplayId,
            UUID patientId, String patientMrn, String patientName,
            UUID bedId, String bedCode,
            int stayValue, StayUnit stayUnit
    ) {
        if (visitId == null || patientId == null || bedId == null) {
            throw new DomainException("ADMISSION_REFS_REQUIRED", "visit, patient and bed are required");
        }
        if (stayUnit == null) {
            throw new DomainException("STAY_UNIT_REQUIRED", "stay unit is required");
        }
        if (stayValue <= 0) {
            throw new DomainException("STAY_VALUE_INVALID", "stay value must be positive");
        }
        PrematureAdmission a = new PrematureAdmission();
        a.id = UUID.randomUUID();
        a.visitId = visitId;
        a.visitDisplayId = visitDisplayId;
        a.patientId = patientId;
        a.patientMrn = patientMrn;
        a.patientName = patientName;
        a.bedId = bedId;
        a.bedCode = bedCode;
        a.status = AdmissionStatus.AWAITING_ADMISSION_PAYMENT;
        a.stayValue = stayValue;
        a.stayUnit = stayUnit;
        a.admittedAt = Instant.now();
        a.stayExpiresAt = a.admittedAt.plus(stayValue, stayUnit.chronoUnit());
        return a;
    }

    public void linkInitialPayment(UUID paymentId) {
        this.initialPaymentId = paymentId;
    }

    public void markUnderCare() {
        require(AdmissionStatus.AWAITING_ADMISSION_PAYMENT, "mark under care");
        this.status = AdmissionStatus.UNDER_CARE;
    }

    public void cancel() {
        require(AdmissionStatus.AWAITING_ADMISSION_PAYMENT, "cancel");
        this.status = AdmissionStatus.CANCELLED;
    }

    public void extendStay(int value, StayUnit unit) {
        if (status != AdmissionStatus.UNDER_CARE && status != AdmissionStatus.TREATMENT_FINISHED) {
            throw new DomainException("ADMISSION_NOT_EXTENDABLE",
                    "Can only extend stay while UNDER_CARE or TREATMENT_FINISHED (status=" + status + ")");
        }
        if (value <= 0 || unit == null) {
            throw new DomainException("STAY_VALUE_INVALID", "extension must be positive with a unit");
        }
        this.stayExpiresAt = this.stayExpiresAt.plus(value, unit.chronoUnit());
    }

    public void finishTreatment() {
        require(AdmissionStatus.UNDER_CARE, "finish treatment");
        this.status = AdmissionStatus.TREATMENT_FINISHED;
        this.treatmentFinishedAt = Instant.now();
    }

    public void scheduleDischargePayment(UUID finalPaymentId) {
        require(AdmissionStatus.TREATMENT_FINISHED, "schedule discharge payment");
        this.finalPaymentId = finalPaymentId;
        this.status = AdmissionStatus.AWAITING_DISCHARGE_PAYMENT;
    }

    /** Re-issue a discharge payment after a prior FINAL payment was rejected (BRD P12b). */
    public void reissueDischargePayment(UUID newFinalPaymentId) {
        require(AdmissionStatus.AWAITING_DISCHARGE_PAYMENT, "re-issue discharge payment");
        this.finalPaymentId = newFinalPaymentId;
        // status stays AWAITING_DISCHARGE_PAYMENT; only the linked payment changes.
    }

    public void close() {
        require(AdmissionStatus.AWAITING_DISCHARGE_PAYMENT, "close");
        this.status = AdmissionStatus.CLOSED;
        this.closedAt = Instant.now();
    }

    private void require(AdmissionStatus expected, String action) {
        if (this.status != expected) {
            throw new DomainException("ADMISSION_INVALID_STATE",
                    "Cannot " + action + " — admission is " + this.status + ", expected " + expected);
        }
    }
}
