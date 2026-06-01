package com.albudoor.hms.emergency.api;

import com.albudoor.hms.catalogue.domain.ServiceItem;

import java.math.BigDecimal;
import java.util.UUID;

public record EmergencyServiceResponse(UUID id, String code, String nameEn, String nameAr,
                                       BigDecimal fee, String currency) {
    public static EmergencyServiceResponse from(ServiceItem s) {
        return new EmergencyServiceResponse(s.getId(), s.getCode(), s.getNameEn(), s.getNameAr(),
                s.getFee(), s.getCurrency());
    }
}
