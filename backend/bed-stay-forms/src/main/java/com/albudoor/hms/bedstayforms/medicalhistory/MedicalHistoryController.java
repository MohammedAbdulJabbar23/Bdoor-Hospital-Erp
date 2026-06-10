package com.albudoor.hms.bedstayforms.medicalhistory;

import com.albudoor.hms.bedstayforms.access.BedStayAccess;
import com.albudoor.hms.bedstayforms.access.CurrentUser;
import com.albudoor.hms.bedstayforms.api.MedicalHistoryDto;
import com.albudoor.hms.bedstayforms.api.MedicalHistoryResponse;
import com.albudoor.hms.bedstayforms.api.SignatureView;
import com.albudoor.hms.bedstayforms.directory.StayDirectoryRegistry;
import com.albudoor.hms.bedstayforms.domain.MedicalHistorySheet;
import com.albudoor.hms.bedstayforms.domain.MhSignatureSlot;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.infrastructure.MedicalHistoryRepository;
import com.albudoor.hms.bedstayforms.signatures.SignatureStore;
import com.albudoor.hms.platform.exception.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/bed-stays/{department}/{stayId}/medical-history")
public class MedicalHistoryController {

    private final MedicalHistoryHandler handler;
    private final BedStayAccess access;
    private final MedicalHistoryRepository sheets;
    private final StayDirectoryRegistry stays;
    private final SignatureStore signatures;

    public MedicalHistoryController(MedicalHistoryHandler handler, BedStayAccess access,
                                    MedicalHistoryRepository sheets, StayDirectoryRegistry stays,
                                    SignatureStore signatures) {
        this.handler = handler;
        this.access = access;
        this.sheets = sheets;
        this.stays = stays;
        this.signatures = signatures;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public MedicalHistoryResponse get(@PathVariable StayDepartment department, @PathVariable UUID stayId) {
        access.checkRead(department);
        return handler.get(department, stayId);
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public MedicalHistoryDto upsert(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                                    @Valid @RequestBody UpsertMedicalHistoryCommand cmd) {
        access.checkDoctorWrite(department);
        return handler.upsert(department, stayId, cmd);
    }

    @PostMapping("/signatures/{slot}")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public SignatureView sign(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                              @PathVariable MhSignatureSlot slot,
                              @RequestParam("file") MultipartFile file,
                              @RequestParam(value = "signerName", required = false) String signerName)
            throws IOException {
        access.checkDoctorWrite(department);
        stays.requireOpen(department, stayId);
        MedicalHistorySheet sheet = sheets.findByDepartmentAndStayId(department, stayId)
                .orElseGet(() -> MedicalHistorySheet.create(department, stayId));
        String key = signatures.store(file);
        sheet.applySignature(slot, key, signerName, CurrentUser.id());
        return SignatureView.from(sheets.save(sheet).signature(slot));
    }

    @GetMapping("/signatures/{slot}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> signature(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                                              @PathVariable MhSignatureSlot slot) throws IOException {
        access.checkRead(department);
        MedicalHistorySheet sheet = sheets.findByDepartmentAndStayId(department, stayId)
                .orElseThrow(() -> new NotFoundException("No medical history for stay " + stayId));
        return signatures.stream(sheet.signature(slot), slot.name());
    }
}
