package com.albudoor.hms.premature.signature;

import com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.platform.storage.FileStorage;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.domain.PrematureForm;
import com.albudoor.hms.premature.domain.Signature;
import com.albudoor.hms.premature.domain.SignatureSlot;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.premature.infrastructure.PrematureFormRepository;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/premature/admissions")
public class SignatureController {

    private final PrematureFormRepository forms;
    private final PrematureAdmissionRepository admissions;
    private final FileStorage storage;

    public SignatureController(PrematureFormRepository forms, PrematureAdmissionRepository admissions, FileStorage storage) {
        this.forms = forms;
        this.admissions = admissions;
        this.storage = storage;
    }

    /** Ack returned after a signature upload. */
    public record Ack(String slot, String signerName, java.time.Instant signedAt) {}

    @PostMapping("/{id}/form/signatures/{slot}")
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'DOCTOR', 'ADMIN')")
    @Transactional
    public Ack upload(
            @PathVariable UUID id, @PathVariable SignatureSlot slot,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "signerName", required = false) String signerName
    ) throws IOException {
        if (file.isEmpty()) throw new DomainException("SIGNATURE_EMPTY", "Signature image is empty");
        PrematureForm form = forms.findByAdmissionId(id).orElseGet(() -> {
            PrematureAdmission adm = admissions.findById(id)
                    .orElseThrow(() -> new NotFoundException("Admission not found: " + id));
            return PrematureForm.create(adm.getId(), adm.getVisitId(), adm.getPatientId());
        });
        String key;
        try (var in = file.getInputStream()) {
            key = storage.save(in, "signature.png", file.getSize());
        }
        form.applySignature(slot, key, signerName, currentUserId());
        forms.save(form);
        Signature s = form.signature(slot);
        return new Ack(slot.name(), s.getSignerName(), s.getSignedAt());
    }

    @GetMapping("/{id}/form/signatures/{slot}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> download(@PathVariable UUID id, @PathVariable SignatureSlot slot) throws IOException {
        PrematureForm form = forms.findByAdmissionId(id)
                .orElseThrow(() -> new NotFoundException("Form not found for admission: " + id));
        Signature s = form.signature(slot);
        if (s == null || s.getImageKey() == null) throw new NotFoundException("No signature in slot " + slot);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(new InputStreamResource(storage.open(s.getImageKey())));
    }

    private static UUID currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof HmsUserPrincipal p) return p.userId();
        return null;
    }
}
