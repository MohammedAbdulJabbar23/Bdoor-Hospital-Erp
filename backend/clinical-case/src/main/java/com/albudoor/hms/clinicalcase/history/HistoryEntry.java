package com.albudoor.hms.clinicalcase.history;

import java.time.Instant;
import java.util.Map;

/**
 * One row in a patient's unified timeline. The {@code department} field holds VisitType names
 * (e.g. DOCTOR_APPOINTMENT, LABORATORY, RADIOLOGY, ECO, PREMATURE, EMERGENCY) or "CLINICAL"
 * for exams.
 *
 * <p>{@code kind} is a stable machine code the frontend translates
 * ({@code patientProfile.timeline.kind.*}) using {@code params} for interpolation; {@code title}
 * and {@code detail} stay as the untranslated fallback.</p>
 */
public record HistoryEntry(Instant at, HistoryEntryType type, String department,
                           String title, String detail, String kind,
                           Map<String, String> params, HistoryRefs refs) {}
