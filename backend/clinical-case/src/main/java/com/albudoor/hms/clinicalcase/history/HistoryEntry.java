package com.albudoor.hms.clinicalcase.history;

import java.time.Instant;

/**
 * One row in a patient's unified timeline. The {@code department} field holds VisitType names
 * (e.g. DOCTOR_APPOINTMENT, LABORATORY, RADIOLOGY, ECO, PREMATURE, EMERGENCY) or "CLINICAL"
 * for exams.
 */
public record HistoryEntry(Instant at, HistoryEntryType type, String department,
                           String title, String detail, HistoryRefs refs) {}
