package com.albudoor.hms.bedstayforms.domain;

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

/**
 * BRD REC-005 §6.6.2 — one row of the Nursing Procedures log. Append-only (no edit/delete
 * slice; matches the paper log + the BRD auditability rule). The nurse is auto-attributed
 * from the authenticated user instead of a drawn per-row sign.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stay_nursing_procedure")
public class NursingProcedureEntry extends AggregateRoot {

    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StayDepartment department;
    @Column(name = "stay_id", nullable = false)
    private UUID stayId;

    @Column(name = "procedure_name", nullable = false, length = 300)
    private String procedureName;
    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;
    @Column(length = 2000)
    private String note;
    @Column(name = "nurse_name", length = 200)
    private String nurseName;
    @Column(name = "recorded_by")
    private UUID recordedBy;

    public static NursingProcedureEntry record(StayDepartment department, UUID stayId,
                                               String procedureName, Instant performedAt,
                                               String note, String nurseName, UUID recordedBy) {
        if (department == null || stayId == null) {
            throw new DomainException("STAY_REF_REQUIRED", "department and stay are required");
        }
        if (procedureName == null || procedureName.isBlank()) {
            throw new DomainException("PROCEDURE_NAME_REQUIRED", "procedure name is required");
        }
        if (performedAt == null) {
            throw new DomainException("PERFORMED_AT_REQUIRED", "performed-at time is required");
        }
        NursingProcedureEntry e = new NursingProcedureEntry();
        e.id = UUID.randomUUID();
        e.department = department;
        e.stayId = stayId;
        e.procedureName = procedureName.trim();
        e.performedAt = performedAt;
        e.note = note;
        e.nurseName = nurseName;
        e.recordedBy = recordedBy;
        return e;
    }
}
