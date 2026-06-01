package com.albudoor.hms.emergency.api;

import com.albudoor.hms.emergency.domain.EmergencyBed;

import java.time.Instant;
import java.util.UUID;

public record BedResponse(
        UUID id,
        String code,
        String room,
        String status,
        boolean active,
        Occupant occupant
) {
    /** Present when the bed is PENDING_PAYMENT or OCCUPIED. */
    public record Occupant(
            UUID caseId,
            UUID visitId,
            String visitDisplayId,
            String patientName,
            String patientMrn,
            String caseStatus,
            Instant stayExpiresAt
    ) {}

    public static BedResponse from(EmergencyBed b) {
        return new BedResponse(b.getId(), b.getCode(), b.getRoom(),
                b.getStatus().name(), b.isActive(), null);
    }

    public BedResponse withOccupant(Occupant occ) {
        return new BedResponse(id, code, room, status, active, occ);
    }
}
