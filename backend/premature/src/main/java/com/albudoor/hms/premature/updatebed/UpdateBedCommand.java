package com.albudoor.hms.premature.updatebed;

import jakarta.validation.constraints.Size;

public record UpdateBedCommand(
        @Size(max = 100) String room,
        boolean active
) {}
