package com.albudoor.hms.bedstayforms.api;

import com.albudoor.hms.bedstayforms.domain.FormSignature;

import java.time.Instant;

public record SignatureView(String signerName, Instant signedAt, boolean present) {
    public static SignatureView from(FormSignature s) {
        if (s == null) return new SignatureView(null, null, false);
        return new SignatureView(s.getSignerName(), s.getSignedAt(), s.present());
    }
}
