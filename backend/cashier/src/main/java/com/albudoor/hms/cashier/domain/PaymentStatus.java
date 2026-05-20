package com.albudoor.hms.cashier.domain;

public enum PaymentStatus {
    /** Awaiting cashier action. */
    PENDING,
    /** Cashier marked paid (or VIP bypass auto-approved). */
    APPROVED,
    /** Cashier rejected — patient could not pay or refused. */
    REJECTED
}
