package com.albudoor.hms.platform.exception;

/** A DB-referenced blob is gone from the storage backend — surfaces as 404 DOCUMENT_MISSING. */
public class StorageMissingException extends RuntimeException {
    public StorageMissingException(String storageKey) {
        super("Stored document is missing: " + storageKey);
    }
}
