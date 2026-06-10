package com.albudoor.hms.bedstayforms.documents;

import com.albudoor.hms.bedstayforms.api.StayDocumentDto;
import com.albudoor.hms.bedstayforms.directory.StayDirectoryRegistry;
import com.albudoor.hms.bedstayforms.directory.StayInfo;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.domain.StayDocument;
import com.albudoor.hms.bedstayforms.infrastructure.StayDocumentRepository;
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

    private final StayDocumentRepository documents;
    private final StayDirectoryRegistry stays;
    private final FileStorage storage;

    public StayDocumentsHandler(StayDocumentRepository documents, StayDirectoryRegistry stays,
                                FileStorage storage) {
        this.documents = documents;
        this.stays = stays;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<StayDocumentDto> list(StayDepartment dept, UUID stayId) {
        stays.require(dept, stayId);
        List<StayDocumentDto> out = new ArrayList<>();
        String base = "/api/bed-stays/" + dept + "/" + stayId + "/documents/";
        for (StayDocument d : documents.findAllByDepartmentAndStayIdOrderByCreatedAtDesc(dept, stayId)) {
            out.add(StayDocumentDto.fromUpload(d, base + d.getId() + "/file"));
        }
        return out;
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
        if (ct == null || !(ct.startsWith("image/") || ct.equals("application/pdf"))) {
            throw new DomainException("DOCUMENT_TYPE_NOT_ALLOWED", "Only images and PDF documents are allowed");
        }
        StoredBlob blob;
        try (var in = file.getInputStream()) {
            blob = storage.saveVerified(in, file.getOriginalFilename() == null ? "document" : file.getOriginalFilename());
        }
        StayDocument d = StayDocument.upload(dept, stayId, info.patientId(),
                file.getOriginalFilename(), ct, blob.sizeBytes(), blob.sha256(), blob.storageKey(),
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
}
