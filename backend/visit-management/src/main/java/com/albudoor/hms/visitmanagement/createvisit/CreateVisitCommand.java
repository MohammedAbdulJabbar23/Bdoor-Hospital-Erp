package com.albudoor.hms.visitmanagement.createvisit;

import com.albudoor.hms.visitmanagement.domain.VisitOrigin;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Create a direct visit. {@code origin} is accepted for backward-compatibility but IGNORED:
 * the server derives new-vs-returning from whether the patient already has any visits
 * (first ever → {@link VisitOrigin#DIRECT_NEW}, otherwise {@link VisitOrigin#DIRECT_RETURNING}).
 */
public record CreateVisitCommand(
        @NotNull UUID patientId,
        @NotNull VisitType visitType,
        VisitOrigin origin,
        UUID assignedDoctorId
) {}
