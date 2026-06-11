package com.albudoor.hms.platform.storage.inventory;

import java.util.List;

public record StorageVerifyResponse(int checked, List<DocumentRef> missing,
                                    List<DocumentRef> corrupt, List<DocumentRef> unreadable,
                                    List<String> orphanedFiles) {}
