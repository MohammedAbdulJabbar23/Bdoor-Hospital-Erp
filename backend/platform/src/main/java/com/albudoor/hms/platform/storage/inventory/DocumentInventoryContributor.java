package com.albudoor.hms.platform.storage.inventory;

import java.util.List;

/** Implemented by modules that store blobs, so the integrity check can walk everything. */
public interface DocumentInventoryContributor {
    List<DocumentRef> documentRefs();
}
