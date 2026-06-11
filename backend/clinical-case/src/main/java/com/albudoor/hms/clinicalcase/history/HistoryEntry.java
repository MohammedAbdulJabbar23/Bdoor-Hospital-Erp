package com.albudoor.hms.clinicalcase.history;

import java.time.Instant;

public record HistoryEntry(Instant at, HistoryEntryType type, String department,
                           String title, String detail, HistoryRefs refs) {}
