package com.albudoor.hms.bedstayforms.nursingprocedures;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record AddNursingProcedureCommand(
        @NotBlank String procedureName,
        @NotNull Instant performedAt,
        String note
) {}
