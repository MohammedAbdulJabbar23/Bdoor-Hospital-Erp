package com.albudoor.hms.pharmacy.domain;

public enum DispenseStatus {
    /** Created from a finalized exam, not yet sent to cashier. */
    PENDING,
    /** A payment has been created in the cashier queue and is awaiting decision. */
    AWAITING_PAYMENT,
    /** Cashier has approved payment; pharmacist needs to physically hand over the meds. */
    READY_TO_GIVE,
    /** Patient received the meds. Terminal. */
    DISPENSED,
    /** Cancelled before dispensing (patient declined, prescription error, etc). Terminal. */
    CANCELLED
}
