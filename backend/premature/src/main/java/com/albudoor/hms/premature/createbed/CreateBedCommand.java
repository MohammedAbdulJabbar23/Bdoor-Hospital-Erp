package com.albudoor.hms.premature.createbed;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBedCommand(
        @NotBlank @Size(max = 30) String code,
        @Size(max = 100) String room
) {}
