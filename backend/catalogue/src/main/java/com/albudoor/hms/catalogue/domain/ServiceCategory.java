package com.albudoor.hms.catalogue.domain;

/**
 * Top-level grouping of service catalogue items.
 *
 * <p>The Lab/Imaging/ECO/Emergency/Drug split mirrors the BRD set: each visit-type that
 * routes to a department reads from one of these categories.
 */
public enum ServiceCategory {
    /** Laboratory tests (HMS-BRD-REC-002). */
    LAB,
    /** Radiology imaging types (HMS-BRD-REC-003). */
    IMAGING,
    /** Echocardiography service types (HMS-BRD-REC-006). */
    ECO,
    /** Emergency department services — the 40-item Albudoor sheet (HMS-BRD-REC-004 §6.6). */
    EMERGENCY,
    /** Pharmacy drug catalogue. */
    DRUG
}
