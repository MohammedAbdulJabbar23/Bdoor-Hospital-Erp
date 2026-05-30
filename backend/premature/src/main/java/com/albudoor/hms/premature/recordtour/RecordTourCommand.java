package com.albudoor.hms.premature.recordtour;

import com.albudoor.hms.premature.domain.RespSupport;
import com.albudoor.hms.premature.domain.TourType;
import com.albudoor.hms.premature.domain.TourVitals;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record RecordTourCommand(
        @NotNull TourType tourType,
        @NotNull Integer respRate, @NotNull Integer spo2, @NotNull Integer pulseRate,
        @NotEmpty List<RespSupport> respSupport,
        String bowelMotion, @NotNull String uop, String feeding, String vomiting, String jaundice,
        String ivAccess, String ivFluid,
        @NotNull BigDecimal babyTempC, BigDecimal incubatorTempC, Integer humidity,
        String nasalSeptum, Integer rbs, String others
) {
    public TourVitals toVitals() {
        Set<RespSupport> rs = (respSupport == null) ? new HashSet<>() : new HashSet<>(respSupport);
        return new TourVitals(respRate, spo2, pulseRate, rs, bowelMotion, uop, feeding, vomiting, jaundice,
                ivAccess, ivFluid, babyTempC, incubatorTempC, humidity, nasalSeptum, rbs, others);
    }
}
