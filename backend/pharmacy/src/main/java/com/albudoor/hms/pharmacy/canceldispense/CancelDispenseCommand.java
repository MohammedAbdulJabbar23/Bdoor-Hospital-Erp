package com.albudoor.hms.pharmacy.canceldispense;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelDispenseCommand(
        @NotBlank @Size(max = 500) String reason
) {}
