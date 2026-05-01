package com.albudoor.hms.visitmanagement.infrastructure;

public interface VisitIdGenerator {
    /** Returns the next visit display id. Default format: {@code VST-YYYY-NNNNNN}. */
    String next();
}
