package com.albudoor.hms.departmentservices.domain;

public enum DepartmentCaseStatus {
    /** Case opened, services not yet picked. */
    NEW,
    /** Services picked + payment created; awaiting cashier. */
    AWAITING_PAYMENT,
    /** Payment approved; physical study/test in progress. */
    AWAITING_STUDY,
    /** All services have findings uploaded; not yet finalised. */
    FINDINGS_COMPLETE,
    /** Direct visit closed. */
    CLOSED,
    /** Forwarded visit returned to originating department. */
    RETURNED,
    /** Cancelled before completion. */
    CANCELLED;

    /** A finalized/terminal case can no longer accept clinical mutations (e.g. attachments). */
    public boolean isTerminal() {
        return this == CLOSED || this == RETURNED || this == CANCELLED;
    }
}
