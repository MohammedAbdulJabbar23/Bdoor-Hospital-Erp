package com.albudoor.hms.bedstayforms.api;

import com.albudoor.hms.bedstayforms.domain.StayDocument;

import java.time.Instant;
import java.util.UUID;

/** One row of the merged documents list. source: UPLOAD | LABORATORY | RADIOLOGY | ECO. */
public record StayDocumentDto(
        UUID id, String source, String fileName, String contentType, long sizeBytes, String sha256,
        String label, UUID uploadedBy, Instant uploadedAt, boolean archived, String fileUrl
) {
    /** A result attachment surfaced from a forwarded Lab/Radiology/ECO visit (no recorded sha). */
    public static StayDocumentDto fromResultAttachment(UUID id, String source, String fileName,
            String contentType, long sizeBytes, UUID uploadedBy, Instant uploadedAt, String fileUrl) {
        return new StayDocumentDto(id, source, fileName, contentType, sizeBytes, null, null,
                uploadedBy, uploadedAt, false, fileUrl);
    }

    public static StayDocumentDto fromUpload(StayDocument d, String fileUrl) {
        return new StayDocumentDto(d.getId(), "UPLOAD", d.getFileName(), d.getContentType(),
                d.getSizeBytes(), d.getSha256(), d.getLabel(), d.getUploadedBy(), d.getCreatedAt(), d.isArchived(), fileUrl);
    }
}
