package com.albudoor.hms.bedstayforms.nursingprocedures;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AddNursingProcedureCommand(
        @NotBlank @Size(max = 300) String procedureName,
        @NotNull Instant performedAt,
        @Size(max = 2000) String note
) {}
