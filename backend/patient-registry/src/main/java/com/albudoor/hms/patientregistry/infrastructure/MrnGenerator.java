package com.albudoor.hms.patientregistry.infrastructure;

public interface MrnGenerator {
    /** Returns the next MRN. Format is implementation-defined (default: ALB-YYYY-######). */
    String next();
}
