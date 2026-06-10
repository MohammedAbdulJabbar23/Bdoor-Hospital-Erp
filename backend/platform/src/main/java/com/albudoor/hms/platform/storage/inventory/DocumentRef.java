package com.albudoor.hms.platform.storage.inventory;

/** One DB-referenced blob: where it lives + its recorded hash (null when not recorded). */
public record DocumentRef(String owner, String refId, String storageKey, String sha256) {}
