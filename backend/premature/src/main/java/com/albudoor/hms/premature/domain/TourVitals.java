package com.albudoor.hms.premature.domain;

import java.math.BigDecimal;
import java.util.Set;

/** The BRD tour-grid vitals captured per tour. Mandatory: respRate, spo2, pulseRate, uop, babyTempC. */
public record TourVitals(
        Integer respRate, Integer spo2, Integer pulseRate,
        Set<RespSupport> respSupport,
        String bowelMotion, String uop, String feeding, String vomiting, String jaundice,
        String ivAccess, String ivFluid,
        BigDecimal babyTempC, BigDecimal incubatorTempC, Integer humidity,
        String nasalSeptum, Integer rbs, String others
) {}
