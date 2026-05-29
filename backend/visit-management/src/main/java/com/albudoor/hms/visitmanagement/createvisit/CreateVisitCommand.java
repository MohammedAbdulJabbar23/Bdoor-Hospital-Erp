package com.albudoor.hms.visitmanagement.createvisit;

import com.albudoor.hms.visitmanagement.domain.VisitOrigin;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Create a direct visit. {@code origin} is optional; when null it defaults to
 * {@link VisitOrigin#DIRECT_RETURNING} for backward-compatibility.
 */
public record CreateVisitCommand(
        @NotNull UUID patientId,
        @NotNull VisitType visitType,
        VisitOrigin origin,
        UUID assignedDoctorId
) {}
