package com.albudoor.hms.premature.api;

import com.albudoor.hms.premature.domain.PrematureTour;
import com.albudoor.hms.premature.domain.RespSupport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PrematureTourResponse(
        UUID id, String tourType, Instant recordedAt, UUID recordedBy,
        Integer respRate, Integer spo2, Integer pulseRate, List<String> respSupport,
        String bowelMotion, String uop, String feeding, String vomiting, String jaundice,
        String ivAccess, String ivFluid, BigDecimal babyTempC, BigDecimal incubatorTempC,
        Integer humidity, String nasalSeptum, Integer rbs, String others
) {
    public static PrematureTourResponse from(PrematureTour t) {
        return new PrematureTourResponse(
                t.getId(), t.getTourType().name(), t.getRecordedAt(), t.getRecordedBy(),
                t.getRespRate(), t.getSpo2(), t.getPulseRate(),
                t.getRespSupport().stream().map(RespSupport::name).sorted().toList(),
                t.getBowelMotion(), t.getUop(), t.getFeeding(), t.getVomiting(), t.getJaundice(),
                t.getIvAccess(), t.getIvFluid(), t.getBabyTempC(), t.getIncubatorTempC(),
                t.getHumidity(), t.getNasalSeptum(), t.getRbs(), t.getOthers());
    }
}
