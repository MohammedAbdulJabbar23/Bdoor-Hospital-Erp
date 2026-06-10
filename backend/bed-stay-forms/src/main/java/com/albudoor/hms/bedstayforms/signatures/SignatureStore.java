package com.albudoor.hms.bedstayforms.signatures;

import com.albudoor.hms.bedstayforms.domain.FormSignature;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.platform.storage.FileStorage;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Shared validate/store/stream logic for drawn-signature images on clinical forms. */
@Component
public class SignatureStore {

    private final FileStorage storage;

    public SignatureStore(FileStorage storage) {
        this.storage = storage;
    }

    /** Validates the multipart image and persists it; returns the storage key. */
    public String store(MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new DomainException("SIGNATURE_EMPTY", "Signature image is empty");
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw new DomainException("SIGNATURE_NOT_IMAGE", "Signature must be an image");
        }
        try (var in = file.getInputStream()) {
            return storage.save(in, "signature.png", file.getSize());
        }
    }

    /** Streams a stored signature, or 404 if the slot is unsigned. */
    public ResponseEntity<Resource> stream(FormSignature signature, String slotLabel) throws IOException {
        if (signature == null || !signature.present()) {
            throw new NotFoundException("No signature in slot " + slotLabel);
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .body(new InputStreamResource(storage.open(signature.getImageKey())));
    }
}
