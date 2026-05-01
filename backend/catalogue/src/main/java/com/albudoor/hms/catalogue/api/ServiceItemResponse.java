package com.albudoor.hms.catalogue.api;

import com.albudoor.hms.catalogue.domain.DrugDetails;
import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.catalogue.domain.ServiceItem;

import java.math.BigDecimal;
import java.util.UUID;

public record ServiceItemResponse(
        UUID id,
        ServiceCategory category,
        String code,
        String nameEn,
        String nameAr,
        String description,
        BigDecimal fee,
        String currency,
        Integer sortOrder,
        boolean active,
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

    public static ServiceItemResponse from(ServiceItem s) {
        DrugPart drug = null;
        if (s.getDrugDetails() != null) {
            DrugDetails d = s.getDrugDetails();
            drug = new DrugPart(d.getGenericName(), d.getDosageForm(), d.getStrength(),
                    d.getUnit(), d.isControlled(), d.getSupplier(), d.getBarcode());
        }
        return new ServiceItemResponse(
                s.getId(), s.getCategory(), s.getCode(), s.getNameEn(), s.getNameAr(),
                s.getDescription(), s.getFee(), s.getCurrency(), s.getSortOrder(),
                s.isActive(), s.getForwardTo(), drug);
    }
}
