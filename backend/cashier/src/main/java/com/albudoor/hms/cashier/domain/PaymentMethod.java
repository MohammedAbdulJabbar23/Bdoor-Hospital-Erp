package com.albudoor.hms.cashier.domain;

public enum PaymentMethod {
    CASH,
    CARD,
    BANK_TRANSFER,
    /** Used when {@code vipBypass=true}. */
    VIP_BYPASS
}
