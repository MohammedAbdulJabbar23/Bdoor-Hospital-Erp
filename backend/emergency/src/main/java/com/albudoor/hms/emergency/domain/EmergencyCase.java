package com.albudoor.hms.emergency.domain;

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
@Table(name = "emerg_case")
public class EmergencyCase extends AggregateRoot {

    @Id private UUID id;
    @Column(name = "visit_id", nullable = false) private UUID visitId;
    @Column(name = "visit_display_id", nullable = false, length = 30) private String visitDisplayId;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "patient_mrn", nullable = false, length = 30) private String patientMrn;
    @Column(name = "patient_name", nullable = false, length = 300) private String patientName;
    @Column(name = "bed_id", nullable = false) private UUID bedId;
    @Column(name = "bed_code", nullable = false, length = 30) private String bedCode;
    @Column(name = "service_item_id", nullable = false) private UUID serviceItemId;
    @Column(name = "service_code", nullable = false, length = 50) private String serviceCode;
    @Column(name = "service_name", nullable = false, length = 300) private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30) private EmergencyCaseStatus status;

    @Column(name = "stay_value", nullable = false) private int stayValue;
    @Enumerated(EnumType.STRING)
    @Column(name = "stay_unit", nullable = false, length = 10) private StayUnit stayUnit;
    @Column(name = "admitted_at", nullable = false) private Instant admittedAt;
    @Column(name = "stay_expires_at", nullable = false) private Instant stayExpiresAt;
    @Column(name = "treatment_finished_at") private Instant treatmentFinishedAt;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "initial_payment_id") private UUID initialPaymentId;
    @Column(name = "final_payment_id") private UUID finalPaymentId;
    @Column(name = "discharge_note") private String dischargeNote;
    @Column(name = "finish_override_reason") private String finishOverrideReason;

    public static EmergencyCase open(
            UUID visitId, String visitDisplayId,
            UUID patientId, String patientMrn, String patientName,
            UUID bedId, String bedCode,
            UUID serviceItemId, String serviceCode, String serviceName,
            int stayValue, StayUnit stayUnit
    ) {
        if (visitId == null || patientId == null || bedId == null) {
            throw new DomainException("CASE_REFS_REQUIRED", "visit, patient and bed are required");
        }
        if (serviceItemId == null || serviceCode == null || serviceName == null) {
            throw new DomainException("SERVICE_REQUIRED", "an emergency service type is required");
        }
        if (stayUnit == null) throw new DomainException("STAY_UNIT_REQUIRED", "stay unit is required");
        if (stayValue <= 0) throw new DomainException("STAY_VALUE_INVALID", "stay value must be positive");
        EmergencyCase c = new EmergencyCase();
        c.id = UUID.randomUUID();
        c.visitId = visitId; c.visitDisplayId = visitDisplayId;
        c.patientId = patientId; c.patientMrn = patientMrn; c.patientName = patientName;
        c.bedId = bedId; c.bedCode = bedCode;
        c.serviceItemId = serviceItemId; c.serviceCode = serviceCode; c.serviceName = serviceName;
        c.status = EmergencyCaseStatus.AWAITING_INITIAL_PAYMENT;
        c.stayValue = stayValue; c.stayUnit = stayUnit;
        c.admittedAt = Instant.now();
        c.stayExpiresAt = c.admittedAt.plus(stayValue, stayUnit.chronoUnit());
        return c;
    }

    public void linkInitialPayment(UUID paymentId) { this.initialPaymentId = paymentId; }

    public void markUnderTreatment() {
        require(EmergencyCaseStatus.AWAITING_INITIAL_PAYMENT, "mark under treatment");
        this.status = EmergencyCaseStatus.UNDER_TREATMENT;
    }

    public void cancel() {
        require(EmergencyCaseStatus.AWAITING_INITIAL_PAYMENT, "cancel");
        this.status = EmergencyCaseStatus.CANCELLED;
    }

    public void extendStay(int value, StayUnit unit) {
        if (status != EmergencyCaseStatus.UNDER_TREATMENT && status != EmergencyCaseStatus.TREATMENT_FINISHED) {
            throw new DomainException("CASE_NOT_EXTENDABLE",
                    "Can only extend while UNDER_TREATMENT or TREATMENT_FINISHED (status=" + status + ")");
        }
        if (value <= 0 || unit == null) throw new DomainException("STAY_VALUE_INVALID", "extension must be positive with a unit");
        this.stayExpiresAt = this.stayExpiresAt.plus(value, unit.chronoUnit());
    }

    public void finishTreatment() {
        require(EmergencyCaseStatus.UNDER_TREATMENT, "finish treatment");
        this.status = EmergencyCaseStatus.TREATMENT_FINISHED;
        this.treatmentFinishedAt = Instant.now();
    }

    public void scheduleDischargePayment(UUID finalPaymentId) {
        require(EmergencyCaseStatus.TREATMENT_FINISHED, "schedule discharge payment");
        this.finalPaymentId = finalPaymentId;
        this.status = EmergencyCaseStatus.AWAITING_DISCHARGE_PAYMENT;
    }

    public void reissueDischargePayment(UUID newFinalPaymentId) {
        require(EmergencyCaseStatus.AWAITING_DISCHARGE_PAYMENT, "re-issue discharge payment");
        this.finalPaymentId = newFinalPaymentId;
    }

    public void close() {
        require(EmergencyCaseStatus.AWAITING_DISCHARGE_PAYMENT, "close");
        this.status = EmergencyCaseStatus.CLOSED;
        this.closedAt = Instant.now();
    }

    public void setDischargeNote(String note) {
        this.dischargeNote = note;
    }

    public void recordFinishOverride(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new DomainException("OVERRIDE_REASON_REQUIRED",
                    "An override reason is required to finish with results pending");
        }
        this.finishOverrideReason = reason;
    }

    private void require(EmergencyCaseStatus expected, String action) {
        if (this.status != expected) {
            throw new DomainException("CASE_INVALID_STATE",
                    "Cannot " + action + " — case is " + this.status + ", expected " + expected);
        }
    }
}
