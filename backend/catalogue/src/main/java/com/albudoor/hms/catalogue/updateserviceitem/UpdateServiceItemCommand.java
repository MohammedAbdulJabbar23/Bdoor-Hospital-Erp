package com.albudoor.hms.catalogue.updateserviceitem;

import com.albudoor.hms.catalogue.addserviceitem.AddServiceItemCommand;
import com.albudoor.hms.catalogue.domain.ServiceCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateServiceItemCommand(
        @NotBlank @Size(max = 300) String nameEn,
        @Size(max = 300) String nameAr,
        @Size(max = 1000) String description,
        @PositiveOrZero BigDecimal fee,
        @Size(max = 10) String currency,
        Integer sortOrder,
        ServiceCategory forwardTo,
        AddServiceItemCommand.DrugPart drug
) {}
