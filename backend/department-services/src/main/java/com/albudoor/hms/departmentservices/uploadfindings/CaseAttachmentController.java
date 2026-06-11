package com.albudoor.hms.departmentservices.uploadfindings;

import com.albudoor.hms.departmentservices.domain.CaseAttachment;
import com.albudoor.hms.departmentservices.domain.DepartmentCase;
import com.albudoor.hms.departmentservices.infrastructure.CaseAttachmentRepository;
import com.albudoor.hms.departmentservices.infrastructure.DepartmentCaseRepository;
import com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.platform.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Per-case file attachments. Backs the BRD §6.6 requirement that lab/rad/eco can attach
 * PDFs/scans/DICOM exports to a case before finalizing.
 *
 * <p>Limits: 25 MB per file (configurable via Spring's default multipart-size). Allowed
 * content types are not enforced server-side beyond multipart parsing — the catalogue is
 * trusted clinical context, and operators upload what the modality produces.
 */
@RestController
@RequestMapping("/api/dept-cases")
public class CaseAttachmentController {

    private static final Logger log = LoggerFactory.getLogger(CaseAttachmentController.class);

    private final DepartmentCaseRepository cases;
    private final CaseAttachmentRepository attachments;
    private final FileStorage storage;

    public CaseAttachmentController(
            DepartmentCaseRepository cases,
            CaseAttachmentRepository attachments,
            FileStorage storage
    ) {
        this.cases = cases;
        this.attachments = attachments;
        this.storage = storage;
    }

    public record AttachmentResponse(
            UUID id,
            UUID caseId,
            UUID serviceItemId,
            String fileName,
            String contentType,
            long sizeBytes,
            Instant uploadedAt,
            UUID uploadedBy
    ) {
        public static AttachmentResponse from(CaseAttachment a) {
            return new AttachmentResponse(
                    a.getId(), a.getCaseId(), a.getServiceItemId(),
                    a.getFileName(), a.getContentType(), a.getSizeBytes(),
                    a.getUploadedAt(), a.getUploadedBy());
        }
    }

    @PostMapping("/{caseId}/services/{serviceItemId}/attachments")
    @PreAuthorize("hasAnyRole('LAB_STAFF', 'RADIOLOGY_STAFF', 'ECO_STAFF', 'ADMIN')")
    @Transactional
    public AttachmentResponse upload(
            @PathVariable UUID caseId,
            @PathVariable UUID serviceItemId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        if (file.isEmpty()) {
            throw new DomainException("ATTACHMENT_EMPTY", "File is empty");
        }
        DepartmentCase deptCase = cases.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Case not found: " + caseId));
        // A finalized/terminal case (CLOSED/RETURNED/CANCELLED) must not accept new
        // attachments — the clinical record is closed.
        if (deptCase.getStatus().isTerminal()) {
            throw new DomainException("CASE_FINALIZED",
                    "Cannot upload attachments to a " + deptCase.getStatus() + " case " + caseId);
        }
        // Service item must belong to this case
        boolean hasLine = deptCase.getServices().stream()
                .anyMatch(l -> l.getServiceItemId().equals(serviceItemId));
        if (!hasLine) {
            throw new DomainException("ATTACHMENT_SERVICE_MISMATCH",
                    "Service item " + serviceItemId + " is not on case " + caseId);
        }

        String storageKey;
        try (var in = file.getInputStream()) {
            storageKey = storage.save(in, file.getOriginalFilename(), file.getSize());
        }
        CaseAttachment a = CaseAttachment.of(
                caseId, serviceItemId,
                safeName(file.getOriginalFilename()),
                safeContentType(file.getContentType()),
                file.getSize(),
                storageKey,
                currentUserId());
        attachments.save(a);
        log.info("Attached file {} ({} bytes) to case {} service {}",
                a.getFileName(), a.getSizeBytes(), caseId, serviceItemId);
        return AttachmentResponse.from(a);
    }

    @GetMapping("/{caseId}/attachments")
    @PreAuthorize("hasAnyRole('LAB_STAFF', 'RADIOLOGY_STAFF', 'ECO_STAFF', 'DOCTOR', 'ADMIN')")
    public List<AttachmentResponse> list(@PathVariable UUID caseId) {
        return attachments.findAllByCaseIdOrderByUploadedAtAsc(caseId).stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    @GetMapping("/attachments/{id}/file")
    @PreAuthorize("hasAnyRole('LAB_STAFF', 'RADIOLOGY_STAFF', 'ECO_STAFF', 'DOCTOR', 'NURSE', 'ADMIN')")
    public ResponseEntity<Resource> download(@PathVariable UUID id) throws IOException {
        CaseAttachment a = attachments.findById(id)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + id));
        var stream = storage.open(a.getStorageKey());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + a.getFileName().replace("\"", "_") + "\"")
                .contentLength(a.getSizeBytes())
                .body(new InputStreamResource(stream));
    }

    @DeleteMapping("/attachments/{id}")
    @PreAuthorize("hasAnyRole('LAB_STAFF', 'RADIOLOGY_STAFF', 'ECO_STAFF', 'ADMIN')")
    @Transactional
    public void delete(@PathVariable UUID id) throws IOException {
        CaseAttachment a = attachments.findById(id)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + id));
        attachments.delete(a);
        try {
            storage.delete(a.getStorageKey());
        } catch (IOException e) {
            // Row already deleted; orphan blob is acceptable.
            log.warn("Could not remove blob {} after DB delete: {}", a.getStorageKey(), e.toString());
        }
    }

    private static String safeName(String n) {
        if (n == null || n.isBlank()) return "attachment";
        // Strip any path components
        String base = n.replaceAll(".*[/\\\\]", "").trim();
        return base.length() > 300 ? base.substring(0, 300) : base;
    }

    private static String safeContentType(String ct) {
        return (ct == null || ct.isBlank()) ? MediaType.APPLICATION_OCTET_STREAM_VALUE : ct;
    }

    private static UUID currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof HmsUserPrincipal p) return p.userId();
        return null;
    }
}
