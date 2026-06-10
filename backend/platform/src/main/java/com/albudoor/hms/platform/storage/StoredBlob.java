package com.albudoor.hms.platform.storage;

/** Result of a verified save: where it lives, what it hashes to, how big it is. */
public record StoredBlob(String storageKey, String sha256, long sizeBytes) {}
