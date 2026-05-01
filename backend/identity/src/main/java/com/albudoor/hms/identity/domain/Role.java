package com.albudoor.hms.identity.domain;

/**
 * Roles in the HMS. Mapped to Spring authorities as ROLE_<NAME>.
 * Permissions are role-based for Phase 1; finer-grained ACLs can be added later.
 */
public enum Role {
    ADMIN,
    RECEPTIONIST,
    DOCTOR,
    NURSE,
    CASHIER,
    LAB_STAFF,
    RADIOLOGY_STAFF,
    ECO_STAFF,
    EMERGENCY_STAFF,
    PREMATURE_STAFF,
    PHARMACIST
}
