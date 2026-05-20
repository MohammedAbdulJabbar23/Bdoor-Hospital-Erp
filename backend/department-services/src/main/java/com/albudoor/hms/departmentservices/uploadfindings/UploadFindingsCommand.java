package com.albudoor.hms.departmentservices.uploadfindings;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record UploadFindingsCommand(
        @NotNull UUID serviceItemId,
        String textFindings,
        BigDecimal numericValue,
        String unit,
        String referenceRange,
        String flag,
        String measurements,
        /** REC-003 §7.1 body region (e.g. "Chest", "Right knee"). Radiology-only. */
        String bodyRegion,
        String comments,
        String diagnosis
) {}
