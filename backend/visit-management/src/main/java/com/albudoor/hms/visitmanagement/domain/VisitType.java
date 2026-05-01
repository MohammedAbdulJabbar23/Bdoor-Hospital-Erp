package com.albudoor.hms.visitmanagement.domain;

/**
 * High-level visit category — drives which workflow handles the visit.
 * Mirrors the BRD set; phantom departments (ICU/CCU/Dialysis/...) are deferred.
 */
public enum VisitType {
    DOCTOR_APPOINTMENT,
    LABORATORY,
    RADIOLOGY,
    ECO,
    EMERGENCY,
    PREMATURE,
    PHARMACY
}
