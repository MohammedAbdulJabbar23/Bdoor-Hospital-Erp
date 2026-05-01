package com.albudoor.hms.visitmanagement.createvisit;

import com.albudoor.hms.visitmanagement.domain.VisitType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Create a direct visit (DIRECT_NEW or DIRECT_RETURNING is auto-detected from the patient
 * — for Phase 1 we always set DIRECT_RETURNING since the patient must already exist).
 */
public record CreateVisitCommand(
        @NotNull UUID patientId,
        @NotNull VisitType visitType,
        UUID assignedDoctorId
) {}
