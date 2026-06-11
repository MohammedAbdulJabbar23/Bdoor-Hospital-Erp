package com.albudoor.hms.bedstayforms.documents;

import com.albudoor.hms.bedstayforms.api.StayDocumentDto;
import com.albudoor.hms.bedstayforms.directory.StayDirectoryRegistry;
import com.albudoor.hms.bedstayforms.directory.StayInfo;
import com.albudoor.hms.bedstayforms.directory.StayOrderRef;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.domain.StayDocument;
import com.albudoor.hms.bedstayforms.infrastructure.StayDocumentRepository;
import com.albudoor.hms.departmentservices.infrastructure.CaseAttachmentRepository;
import com.albudoor.hms.departmentservices.infrastructure.DepartmentCaseRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.platform.storage.FileStorage;
import com.albudoor.hms.platform.storage.StoredBlob;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class StayDocumentsHandler {

    static final long MAX_BYTES = 20L * 1024 * 1024;

    static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp", "image/tiff",
            "application/pdf");

    private final StayDocumentRepository documents;
    private final StayDirectoryRegistry stays;
    private final FileStorage storage;
    private final DepartmentCaseRepository deptCases;
    private final CaseAttachmentRepository caseAttachments;

    public StayDocumentsHandler(StayDocumentRepository documents, StayDirectoryRegistry stays,
                                FileStorage storage, DepartmentCaseRepository deptCases,
                                CaseAttachmentRepository caseAttachments) {
        this.documents = documents;
        this.stays = stays;
        this.storage = storage;
        this.deptCases = deptCases;
        this.caseAttachments = caseAttachments;
    }

    @Transactional(readOnly = true)
    public List<StayDocumentDto> list(StayDepartment dept, UUID stayId) {
        stays.require(dept, stayId);
        List<StayDocumentDto> out = new ArrayList<>();
        String base = "/api/bed-stays/" + dept + "/" + stayId + "/documents/";
        for (StayDocument d : documents.findAllByDepartmentAndStayIdOrderByCreatedAtDesc(dept, stayId)) {
            out.add(StayDocumentDto.fromUpload(d, base + d.getId() + "/file"));
        }
        for (StayOrderRef order : stays.directory(dept).orders(stayId)) {
            deptCases.findByVisitId(order.visitId()).ifPresent(c ->
                    caseAttachments.findAllByCaseIdOrderByUploadedAtAsc(c.getId()).forEach(a ->
                            out.add(StayDocumentDto.fromResultAttachment(a.getId(), order.targetType(),
                                    a.getFileName(), a.getContentType(), a.getSizeBytes(),
                                    a.getUploadedBy(), a.getUploadedAt(),
                                    base + "results/" + a.getId() + "/file"))));
        }
        out.sort(java.util.Comparator.comparing(StayDocumentDto::uploadedAt).reversed());
        return out;
    }

    @Transactional(readOnly = true)
    public com.albudoor.hms.departmentservices.domain.CaseAttachment requireResultAttachment(
            StayDepartment dept, UUID stayId, UUID attachmentId) {
        stays.require(dept, stayId);
        var attachment = caseAttachments.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));
        boolean belongs = stays.directory(dept).orders(stayId).stream()
                .map(o -> deptCases.findByVisitId(o.visitId()))
                .flatMap(java.util.Optional::stream)
                .anyMatch(c -> c.getId().equals(attachment.getCaseId()));
        if (!belongs) throw new NotFoundException("Attachment not found on this stay: " + attachmentId);
        return attachment;
    }

    @Transactional
    public StayDocumentDto upload(StayDepartment dept, UUID stayId, MultipartFile file,
                                  String label, UUID uploadedBy) throws IOException {
        StayInfo info = stays.requireOpen(dept, stayId);
        if (file.isEmpty()) throw new DomainException("DOCUMENT_EMPTY", "Document is empty");
        if (file.getSize() > MAX_BYTES) {
            throw new DomainException("DOCUMENT_TOO_LARGE", "Documents are limited to 20 MB");
        }
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_TYPES.contains(ct)) {
            throw new DomainException("DOCUMENT_TYPE_NOT_ALLOWED", "Only images and PDF documents are allowed");
        }
        if (label != null && label.length() > 200) {
            throw new DomainException("DOCUMENT_LABEL_TOO_LONG", "Label is limited to 200 characters");
        }
        String fileName = safeName(file.getOriginalFilename());
        StoredBlob blob;
        try (var in = file.getInputStream()) {
            blob = storage.saveVerified(in, fileName);
        }
        StayDocument d = StayDocument.upload(dept, stayId, info.patientId(),
                fileName, ct, blob.sizeBytes(), blob.sha256(), blob.storageKey(),
                (label == null || label.isBlank()) ? null : label.trim(), uploadedBy);
        String base = "/api/bed-stays/" + dept + "/" + stayId + "/documents/";
        return StayDocumentDto.fromUpload(documents.save(d), base + d.getId() + "/file");
    }

    @Transactional
    public StayDocumentDto archive(StayDepartment dept, UUID stayId, UUID documentId) {
        stays.requireOpen(dept, stayId);
        StayDocument d = requireDoc(dept, stayId, documentId);
        d.archive();
        String base = "/api/bed-stays/" + dept + "/" + stayId + "/documents/";
        return StayDocumentDto.fromUpload(documents.save(d), base + d.getId() + "/file");
    }

    @Transactional(readOnly = true)
    public StayDocument requireDoc(StayDepartment dept, UUID stayId, UUID documentId) {
        StayDocument d = documents.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document not found: " + documentId));
        if (d.getDepartment() != dept || !d.getStayId().equals(stayId)) {
            throw new NotFoundException("Document not found on this stay: " + documentId);
        }
        return d;
    }

    /** Mirrors CaseAttachmentController.safeName: strips path components, blanks fall back, truncates. */
    private static String safeName(String n) {
        if (n == null || n.isBlank()) return "document";
        // Strip any path components
        String base = n.replaceAll(".*[/\\\\]", "").trim();
        if (base.isBlank()) return "document";
        return base.length() > 300 ? base.substring(0, 300) : base;
    }
}
