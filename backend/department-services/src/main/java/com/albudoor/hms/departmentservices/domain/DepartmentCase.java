package com.albudoor.hms.departmentservices.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.visitmanagement.domain.VisitOrigin;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One Lab/Radiology/ECO case, 1:1 with a {@code Visit}.
 *
 * <p>Lifecycle: {@code NEW → AWAITING_PAYMENT → AWAITING_STUDY → FINDINGS_COMPLETE → CLOSED|RETURNED}.
 * Cancellable from any non-terminal state.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "department_case",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dept_case_visit", columnNames = "visit_id"),
        indexes = {
                @Index(name = "idx_dept_case_category_status", columnList = "category, status"),
                @Index(name = "idx_dept_case_visit", columnList = "visit_id"),
                @Index(name = "idx_dept_case_patient", columnList = "patient_id")
        }
)
public class DepartmentCase extends AggregateRoot {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DepartmentCategory category;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "visit_display_id", nullable = false, length = 30)
    private String visitDisplayId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_origin", nullable = false, length = 30)
    private VisitOrigin visitOrigin;

    /** When the visit is FORWARDED, points to the originating parent visit. */
    @Column(name = "parent_visit_id")
    private UUID parentVisitId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "patient_mrn", nullable = false, length = 30)
    private String patientMrn;

    @Column(name = "patient_name", nullable = false, length = 300)
    private String patientName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DepartmentCaseStatus status;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "results_summary", length = 4000)
    private String resultsSummary;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "department_case_service",
            joinColumns = @JoinColumn(name = "case_id")
    )
    @OrderColumn(name = "line_no")
    private List<CaseServiceLine> services = new ArrayList<>();

    public static DepartmentCase open(
            DepartmentCategory category,
            UUID visitId, String visitDisplayId,
            VisitOrigin visitOrigin, UUID parentVisitId,
            UUID patientId, String patientMrn, String patientName
    ) {
        if (visitOrigin == VisitOrigin.FORWARDED && parentVisitId == null) {
            throw new DomainException("PARENT_REQUIRED",
                    "Forwarded visits must have a parentVisitId");
        }
        DepartmentCase c = new DepartmentCase();
        c.id = UUID.randomUUID();
        c.category = category;
        c.visitId = visitId;
        c.visitDisplayId = visitDisplayId;
        c.visitOrigin = visitOrigin;
        c.parentVisitId = parentVisitId;
        c.patientId = patientId;
        c.patientMrn = patientMrn;
        c.patientName = patientName;
        c.status = DepartmentCaseStatus.NEW;
        return c;
    }

    public void addService(CaseServiceLine line) {
        if (status != DepartmentCaseStatus.NEW && status != DepartmentCaseStatus.AWAITING_STUDY) {
            throw new DomainException("CASE_LOCKED",
                    "Cannot add services to a case in status " + status);
        }
        if (services.stream().anyMatch(s -> s.getServiceItemId().equals(line.getServiceItemId()))) {
            throw new DomainException("SERVICE_DUPLICATE",
                    "Service " + line.getServiceCode() + " is already on the case");
        }
        services.add(line);
    }

    public void removeService(UUID serviceItemId) {
        if (status != DepartmentCaseStatus.NEW) {
            throw new DomainException("CASE_LOCKED",
                    "Services can only be removed before payment");
        }
        services.removeIf(s -> s.getServiceItemId().equals(serviceItemId));
    }

    public void linkPayment(UUID paymentId) {
        if (services.isEmpty()) {
            throw new DomainException("NO_SERVICES",
                    "Cannot route to cashier with no services selected");
        }
        this.paymentId = paymentId;
        this.status = DepartmentCaseStatus.AWAITING_PAYMENT;
    }

    /** Called by the payment-bridge listener when the case's payment is approved. */
    public void onPaymentApproved() {
        if (status == DepartmentCaseStatus.AWAITING_PAYMENT) {
            this.status = DepartmentCaseStatus.AWAITING_STUDY;
        }
    }

    public void uploadFindings(
            UUID serviceItemId,
            String textFindings,
            java.math.BigDecimal numericValue,
            String unit, String referenceRange, String flag,
            String measurements, String bodyRegion, String comments, String diagnosis,
            UUID uploadedBy
    ) {
        if (status != DepartmentCaseStatus.AWAITING_STUDY) {
            throw new DomainException("CASE_NOT_AWAITING_STUDY",
                    "Cannot upload findings in status " + status);
        }
        int idx = -1;
        for (int i = 0; i < services.size(); i++) {
            if (services.get(i).getServiceItemId().equals(serviceItemId)) { idx = i; break; }
        }
        if (idx < 0) {
            throw new DomainException("SERVICE_NOT_ON_CASE",
                    "Service item " + serviceItemId + " is not on this case");
        }
        CaseServiceLine updated = services.get(idx).withFindings(
                textFindings, numericValue, unit, referenceRange, flag,
                measurements, bodyRegion, comments, diagnosis, uploadedBy);
        services.set(idx, updated);

        boolean allComplete = services.stream()
                .allMatch(s -> s.getLineStatus() == CaseServiceLine.LineStatus.RESULT_UPLOADED);
        if (allComplete) {
            this.status = DepartmentCaseStatus.FINDINGS_COMPLETE;
        }
    }

    /** Builds a short text summary used when returning a forwarded case to its parent. */
    public String buildResultsSummary() {
        if (services.isEmpty()) return "No findings.";
        StringBuilder sb = new StringBuilder();
        for (CaseServiceLine s : services) {
            sb.append(s.getServiceCode()).append(' ');
            if (s.getNumericValue() != null) {
                sb.append(s.getNumericValue());
                if (s.getUnit() != null) sb.append(' ').append(s.getUnit());
                if (s.getReferenceRange() != null) sb.append(" (ref ").append(s.getReferenceRange()).append(')');
                if (s.getFlag() != null && !"NORMAL".equalsIgnoreCase(s.getFlag())) {
                    sb.append(" [").append(s.getFlag()).append(']');
                }
            } else if (s.getTextFindings() != null && !s.getTextFindings().isBlank()) {
                String t = s.getTextFindings().trim();
                if (t.length() > 80) t = t.substring(0, 77) + "…";
                sb.append(t);
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    public void markFinalized(DepartmentCaseStatus terminal, String summary) {
        if (terminal != DepartmentCaseStatus.CLOSED && terminal != DepartmentCaseStatus.RETURNED) {
            throw new DomainException("INVALID_TERMINAL",
                    "Terminal status must be CLOSED or RETURNED");
        }
        if (status != DepartmentCaseStatus.FINDINGS_COMPLETE) {
            throw new DomainException("CASE_NOT_COMPLETE",
                    "Case is not yet at FINDINGS_COMPLETE");
        }
        this.status = terminal;
        this.finalizedAt = Instant.now();
        this.resultsSummary = summary;
    }

    public Optional<UUID> findServiceItem(UUID serviceItemId) {
        return services.stream()
                .map(CaseServiceLine::getServiceItemId)
                .filter(id -> id.equals(serviceItemId))
                .findFirst();
    }

    public boolean isForwarded() {
        return visitOrigin == VisitOrigin.FORWARDED;
    }
}
