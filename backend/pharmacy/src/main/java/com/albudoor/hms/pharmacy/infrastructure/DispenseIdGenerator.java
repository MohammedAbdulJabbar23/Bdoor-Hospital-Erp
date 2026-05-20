package com.albudoor.hms.pharmacy.infrastructure;

public interface DispenseIdGenerator {
    /** Returns the next dispense display id. Default format: {@code RX-YYYY-NNNNNN}. */
    String next();
}
