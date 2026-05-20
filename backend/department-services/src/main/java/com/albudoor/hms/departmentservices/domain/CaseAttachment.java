package com.albudoor.hms.departmentservices.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * File attached to a department case line — PDF reports, scan images, DICOM exports, etc.
 * Bytes live in the {@code FileStorage}; the row carries metadata + an opaque storage key.
 */
@Entity
@Table(
    name = "case_attachment",
    indexes = {
        @Index(name = "idx_case_attachment_case", columnList = "case_id"),
        @Index(name = "idx_case_attachment_case_service", columnList = "case_id, service_item_id"),
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseAttachment {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "service_item_id", nullable = false)
    private UUID serviceItemId;

    @Column(name = "file_name", nullable = false, length = 300)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    public static CaseAttachment of(
            UUID caseId, UUID serviceItemId,
            String fileName, String contentType, long sizeBytes,
            String storageKey, UUID uploadedBy
    ) {
        CaseAttachment a = new CaseAttachment();
        a.id = UUID.randomUUID();
        a.caseId = caseId;
        a.serviceItemId = serviceItemId;
        a.fileName = fileName;
        a.contentType = contentType;
        a.sizeBytes = sizeBytes;
        a.storageKey = storageKey;
        a.uploadedAt = Instant.now();
        a.uploadedBy = uploadedBy;
        return a;
    }
}
