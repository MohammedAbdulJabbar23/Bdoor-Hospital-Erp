package com.albudoor.hms.catalogue.addserviceitem;

import com.albudoor.hms.catalogue.domain.ServiceCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AddServiceItemCommand(
        @NotNull ServiceCategory category,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 300) String nameEn,
        @Size(max = 300) String nameAr,
        @Size(max = 1000) String description,
        @PositiveOrZero BigDecimal fee,
        @Size(max = 10) String currency,
        Integer sortOrder,
        ServiceCategory forwardTo,
        DrugPart drug
) {
    public record DrugPart(
            String genericName,
            String dosageForm,
            String strength,
            String unit,
            boolean controlled,
            String supplier,
            String barcode
    ) {}
}
