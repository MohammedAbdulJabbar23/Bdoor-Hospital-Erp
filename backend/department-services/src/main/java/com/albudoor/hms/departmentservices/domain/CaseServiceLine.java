package com.albudoor.hms.departmentservices.domain;

import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row on a {@link DepartmentCase} — a single ordered service item plus its findings.
 * The findings shape is union-typed across the three departments:
 * <ul>
 *   <li>Lab uses {@code numericValue}, {@code unit}, {@code referenceRange}, {@code flag}.</li>
 *   <li>Radiology uses {@code textFindings} + {@code measurements}.</li>
 *   <li>ECO uses {@code textFindings} + {@code measurements} (cardiac measurements like EF%).</li>
 * </ul>
 * All have shared {@code comments} and {@code diagnosis}.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CaseServiceLine {

    @Column(name = "service_item_id", nullable = false)
    private UUID serviceItemId;

    @Column(name = "service_code", nullable = false, length = 50)
    private String serviceCode;

    @Column(name = "service_name", nullable = false, length = 300)
    private String serviceName;

    @Column(name = "fee", precision = 12, scale = 2)
    private BigDecimal fee;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_status", nullable = false, length = 20)
    private LineStatus lineStatus;

    // ---------- Findings (all optional; populated on upload) ----------

    @Column(name = "text_findings", length = 4000)
    private String textFindings;

    @Column(name = "numeric_value", precision = 18, scale = 4)
    private BigDecimal numericValue;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "reference_range", length = 100)
    private String referenceRange;

    /** HIGH / LOW / NORMAL / CRITICAL — for lab values. */
    @Column(name = "flag", length = 20)
    private String flag;

    @Column(name = "measurements", length = 2000)
    private String measurements;

    /** Radiology body region (REC-003 §7.1). Optional; only set on radiology cases. */
    @Column(name = "body_region", length = 100)
    private String bodyRegion;

    @Column(name = "comments", length = 2000)
    private String comments;

    @Column(name = "diagnosis", length = 2000)
    private String diagnosis;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    public enum LineStatus {
        PENDING,
        RESULT_UPLOADED
    }

    public static CaseServiceLine pending(
            UUID serviceItemId, String code, String name, BigDecimal fee
    ) {
        return new CaseServiceLine(
                serviceItemId, code, name, fee, LineStatus.PENDING,
                null, null, null, null, null, null, null, null, null, null, null
        );
    }

    /** Returns a new instance with the supplied findings; immutable update for clarity. */
    public CaseServiceLine withFindings(
            String textFindings,
            BigDecimal numericValue,
            String unit,
            String referenceRange,
            String flag,
            String measurements,
            String bodyRegion,
            String comments,
            String diagnosis,
            UUID uploadedBy
    ) {
        boolean hasAny = textFindings != null || numericValue != null
                || measurements != null || comments != null || diagnosis != null;
        if (!hasAny) {
            throw new DomainException("NO_FINDINGS",
                    "At least one findings field must be supplied for " + this.serviceCode);
        }
        return new CaseServiceLine(
                this.serviceItemId, this.serviceCode, this.serviceName, this.fee,
                LineStatus.RESULT_UPLOADED,
                textFindings, numericValue, unit, referenceRange, flag,
                measurements, bodyRegion, comments, diagnosis,
                Instant.now(), uploadedBy
        );
    }
}
