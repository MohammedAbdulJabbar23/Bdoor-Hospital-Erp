package com.albudoor.hms.emergency.domain;

public enum BedStatus {
    /** Free for assignment. */
    AVAILABLE,
    /** Assigned to an admission whose initial payment is not yet approved. */
    PENDING_PAYMENT,
    /** Initial payment approved; patient under care. */
    OCCUPIED
}
