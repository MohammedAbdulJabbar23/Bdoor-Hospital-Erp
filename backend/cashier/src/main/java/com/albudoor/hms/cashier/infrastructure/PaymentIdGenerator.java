package com.albudoor.hms.cashier.infrastructure;

public interface PaymentIdGenerator {
    /** Returns the next payment display id. Default format: {@code PAY-YYYY-NNNNNN}. */
    String next();
}
