package com.albudoor.hms.bedstayforms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A captured signature: image stored in FileStorage (imageKey) + who/when metadata. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FormSignature {
    @Column(name = "sign_key", length = 500)
    private String imageKey;
    @Column(name = "sign_name", length = 200)
    private String signerName;
    @Column(name = "signed_by")
    private UUID signedBy;
    @Column(name = "signed_at")
    private Instant signedAt;

    public static FormSignature empty() { return new FormSignature(null, null, null, null); }
    public boolean present() { return imageKey != null; }
}
