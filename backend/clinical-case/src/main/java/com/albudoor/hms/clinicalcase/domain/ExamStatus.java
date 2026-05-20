package com.albudoor.hms.clinicalcase.domain;

public enum ExamStatus {
    /** Doctor is editing; autosaves overwrite. */
    DRAFT,
    /** Locked. Read-only for everyone except admin re-open. */
    FINALIZED
}
