package com.albudoor.hms.bedstayforms.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BRD REC-005 §6.6.3 — Treatment Chart: one chart per stay per date, N medicine rows
 * (the paper's 11-row limit is a page artifact — "additional pages supported").
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stay_treatment_chart")
public class TreatmentChart extends AggregateRoot {

    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StayDepartment department;
    @Column(name = "stay_id", nullable = false)
    private UUID stayId;
    @Column(name = "chart_date", nullable = false)
    private LocalDate chartDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "stay_treatment_chart_row", joinColumns = @JoinColumn(name = "chart_id"))
    @OrderColumn(name = "position")
    private List<TreatmentRow> rows = new ArrayList<>();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "imageKey", column = @Column(name = "doctor_sign_key", length = 500)),
            @AttributeOverride(name = "signerName", column = @Column(name = "doctor_sign_name", length = 200)),
            @AttributeOverride(name = "signedBy", column = @Column(name = "doctor_signed_by")),
            @AttributeOverride(name = "signedAt", column = @Column(name = "doctor_signed_at")),
    })
    private FormSignature doctorSignature = FormSignature.empty();

    public static TreatmentChart create(StayDepartment department, UUID stayId, LocalDate chartDate) {
        if (department == null || stayId == null) {
            throw new DomainException("STAY_REF_REQUIRED", "department and stay are required");
        }
        if (chartDate == null) {
            throw new DomainException("CHART_DATE_REQUIRED", "chart date is required");
        }
        TreatmentChart c = new TreatmentChart();
        c.id = UUID.randomUUID();
        c.department = department;
        c.stayId = stayId;
        c.chartDate = chartDate;
        return c;
    }

    /** Form-style editing: each save replaces the full row set (like the premature form upsert). */
    public void replaceRows(List<TreatmentRow> newRows) {
        List<TreatmentRow> safe = newRows == null ? List.of() : newRows;
        for (TreatmentRow r : safe) {
            if (r.getMedicineName() == null || r.getMedicineName().isBlank()) {
                throw new DomainException("MEDICINE_NAME_REQUIRED", "each row needs a medicine name");
            }
        }
        this.rows.clear();
        this.rows.addAll(safe);
    }

    public void applyDoctorSignature(String imageKey, String signerName, UUID signedBy) {
        this.doctorSignature = new FormSignature(imageKey, signerName, signedBy, Instant.now());
    }

    public FormSignature getDoctorSignature() {
        return doctorSignature == null ? FormSignature.empty() : doctorSignature;
    }
}
