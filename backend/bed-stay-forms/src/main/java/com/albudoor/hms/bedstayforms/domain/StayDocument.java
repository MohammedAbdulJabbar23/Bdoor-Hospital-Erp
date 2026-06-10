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

import java.util.UUID;

/**
 * A document uploaded directly onto a bed-stay (scanned forms, referral letters, the BRD's
 * P7c statistics sheets). Archived, never deleted (BRD §8.2).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stay_document")
public class StayDocument extends AggregateRoot {

    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StayDepartment department;
    @Column(name = "stay_id", nullable = false)
    private UUID stayId;
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;
    @Column(name = "file_name", nullable = false, length = 300)
    private String fileName;
    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;
    @Column(length = 200)
    private String label;
    @Column(name = "uploaded_by")
    private UUID uploadedBy;
    @Column(nullable = false)
    private boolean archived;

    public static StayDocument upload(StayDepartment department, UUID stayId, UUID patientId,
                                      String fileName, String contentType, long sizeBytes,
                                      String sha256, String storageKey, String label, UUID uploadedBy) {
        if (department == null || stayId == null || patientId == null) {
            throw new DomainException("STAY_REF_REQUIRED", "department, stay and patient are required");
        }
        if (storageKey == null || sha256 == null) {
            throw new DomainException("BLOB_REF_REQUIRED", "storage key and checksum are required");
        }
        StayDocument d = new StayDocument();
        d.id = UUID.randomUUID();
        d.department = department;
        d.stayId = stayId;
        d.patientId = patientId;
        d.fileName = fileName;
        d.contentType = contentType;
        d.sizeBytes = sizeBytes;
        d.sha256 = sha256;
        d.storageKey = storageKey;
        d.label = label;
        d.uploadedBy = uploadedBy;
        d.archived = false;
        return d;
    }

    public void archive() { this.archived = true; }
}
