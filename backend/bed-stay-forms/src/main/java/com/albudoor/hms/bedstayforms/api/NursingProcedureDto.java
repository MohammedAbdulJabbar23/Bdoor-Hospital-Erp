package com.albudoor.hms.bedstayforms.api;

import com.albudoor.hms.bedstayforms.domain.NursingProcedureEntry;

import java.time.Instant;
import java.util.UUID;

public record NursingProcedureDto(UUID id, String procedureName, Instant performedAt,
                                  String note, String nurseName, Instant recordedAt) {
    public static NursingProcedureDto from(NursingProcedureEntry e) {
        return new NursingProcedureDto(e.getId(), e.getProcedureName(), e.getPerformedAt(),
                e.getNote(), e.getNurseName(), e.getCreatedAt());
    }
}
