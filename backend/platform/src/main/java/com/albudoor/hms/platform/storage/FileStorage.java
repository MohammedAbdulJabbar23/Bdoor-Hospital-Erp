package com.albudoor.hms.platform.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Pluggable blob store for clinical attachments (lab/rad/eco results, scans, DICOM, etc.).
 *
 * <p>Phase 1 uses {@link LocalFileSystemStorage}; later phases can swap in S3/MinIO/GCS by
 * providing a different {@code @Bean FileStorage} without touching call sites.
 */
public interface FileStorage {

    /** Persist bytes and return an opaque storage key suitable for {@link #open}. */
    String save(InputStream in, String suggestedName, long sizeBytes) throws IOException;

    /** Open a previously-saved blob. Caller must close. */
    InputStream open(String storageKey) throws IOException;

    /** Best-effort delete; returns false if the underlying object was already gone. */
    boolean delete(String storageKey) throws IOException;

    /**
     * Persist bytes computing SHA-256 + size in the same pass. Implementations must compute
     * the hash during the write (single pass). The returned sha256 is lowercase hex.
     * The given stream is consumed and closed.
     */
    StoredBlob saveVerified(InputStream in, String suggestedName) throws IOException;
}
