package com.albudoor.hms.bedstayforms.directory;

import com.albudoor.hms.bedstayforms.domain.StayDepartment;

import java.util.Optional;
import java.util.UUID;

/**
 * Port answered by each bed-stay department module (premature, emergency): does this stay
 * exist, is it still open for writes, and the patient prefill the paper forms print
 * automatically (Pt. Name / Pt. Code / Age / DOA).
 */
public interface StayDirectory {
    StayDepartment department();
    Optional<StayInfo> find(UUID stayId);

    /** Forwarded Lab/Radiology/ECO orders placed from this stay (child visits). */
    java.util.List<StayOrderRef> orders(UUID stayId);
}
