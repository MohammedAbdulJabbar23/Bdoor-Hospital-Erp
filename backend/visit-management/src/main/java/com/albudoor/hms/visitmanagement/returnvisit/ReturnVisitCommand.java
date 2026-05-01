package com.albudoor.hms.visitmanagement.returnvisit;

import jakarta.validation.constraints.Size;

public record ReturnVisitCommand(
        @Size(max = 2000) String resultsSummary
) {}
