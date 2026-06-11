package com.albudoor.hms.clinicalcase.history;

import java.util.UUID;

public record HistoryRefs(UUID visitId, UUID stayId, UUID documentId, String fileUrl) {
    public static HistoryRefs none() { return new HistoryRefs(null, null, null, null); }
    public static HistoryRefs stay(UUID stayId) { return new HistoryRefs(null, stayId, null, null); }
    public static HistoryRefs visit(UUID visitId) { return new HistoryRefs(visitId, null, null, null); }
    public static HistoryRefs document(UUID documentId, String fileUrl) { return new HistoryRefs(null, null, documentId, fileUrl); }
}
