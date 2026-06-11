package com.albudoor.hms.clinicalcase.history;

import java.util.List;
import java.util.UUID;

/** Implemented by modules that own part of a patient's record (same pattern as StayDirectory). */
public interface HistoryContributor {
    List<HistoryEntry> contribute(UUID patientId);
}
