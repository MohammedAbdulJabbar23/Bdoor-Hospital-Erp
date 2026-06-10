package com.albudoor.hms.bedstayforms.documents;

import com.albudoor.hms.bedstayforms.access.BedStayAccess;
import com.albudoor.hms.bedstayforms.access.CurrentUser;
import com.albudoor.hms.bedstayforms.api.StayDocumentDto;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.domain.StayDocument;
import com.albudoor.hms.platform.storage.FileStorage;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bed-stays/{department}/{stayId}/documents")
public class StayDocumentsController {

    private final StayDocumentsHandler handler;
    private final BedStayAccess access;
    private final FileStorage storage;

    public StayDocumentsController(StayDocumentsHandler handler, BedStayAccess access, FileStorage storage) {
        this.handler = handler;
        this.access = access;
        this.storage = storage;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<StayDocumentDto> list(@PathVariable StayDepartment department, @PathVariable UUID stayId) {
        access.checkRead(department);
        return handler.list(department, stayId);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public StayDocumentDto upload(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                                  @RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "label", required = false) String label)
            throws IOException {
        access.checkRead(department); // read-level: dept staff, DOCTOR, NURSE, ADMIN may upload
        return handler.upload(department, stayId, file, label, CurrentUser.id());
    }

    @GetMapping("/{documentId}/file")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> file(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                                         @PathVariable UUID documentId) throws IOException {
        access.checkRead(department);
        StayDocument d = handler.requireDoc(department, stayId, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.inline()
                                .filename(d.getFileName(), java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .contentLength(d.getSizeBytes())
                .body(new InputStreamResource(storage.open(d.getStorageKey())));
    }

    @PostMapping("/{documentId}/archive")
    @PreAuthorize("isAuthenticated()")
    public StayDocumentDto archive(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                                   @PathVariable UUID documentId) {
        access.checkDoctorWrite(department); // dept staff, DOCTOR, ADMIN — nurses may not archive
        return handler.archive(department, stayId, documentId);
    }
}
