package com.albudoor.hms.premature.domain;

import com.albudoor.hms.platform.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrematureTourTest {

    private TourVitals vitals() {
        return new TourVitals(40, 96, 140, Set.of(RespSupport.CPAP, RespSupport.NC),
                "Normal", "2 ml/kg", "EBM", "No", "No", "Right hand", "D10 4ml/h",
                new BigDecimal("36.8"), new BigDecimal("34.0"), 60, "Intact", 85, "stable");
    }

    @Test
    void records_a_morning_tour_with_vitals() {
        UUID adm = UUID.randomUUID();
        PrematureTour t = PrematureTour.record(adm, TourType.MORNING, UUID.randomUUID(), vitals());
        assertThat(t.getAdmissionId()).isEqualTo(adm);
        assertThat(t.getTourType()).isEqualTo(TourType.MORNING);
        assertThat(t.getRecordedAt()).isNotNull();
        assertThat(t.getRespRate()).isEqualTo(40);
        assertThat(t.getRespSupport()).containsExactlyInAnyOrder(RespSupport.CPAP, RespSupport.NC);
        assertThat(t.getBabyTempC()).isEqualByComparingTo("36.8");
    }

    @Test
    void requires_admission_and_type() {
        assertThatThrownBy(() -> PrematureTour.record(null, TourType.NIGHT, UUID.randomUUID(), vitals()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> PrematureTour.record(UUID.randomUUID(), null, UUID.randomUUID(), vitals()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void requires_mandatory_vitals_resp_rate_spo2_pulse_uop_temp() {
        TourVitals missing = new TourVitals(null, 96, 140, Set.of(RespSupport.NC),
                null, "x", null, null, null, null, null, new BigDecimal("36.8"), null, null, null, null, null);
        assertThatThrownBy(() -> PrematureTour.record(UUID.randomUUID(), TourType.MORNING, UUID.randomUUID(), missing))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void requires_resp_support() {
        TourVitals noRs = new TourVitals(40, 96, 140, java.util.Set.of(),
                "Normal", "2 ml/kg", "EBM", "No", "No", "Right hand", "D10 4ml/h",
                new java.math.BigDecimal("36.8"), null, null, null, null, null);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> PrematureTour.record(java.util.UUID.randomUUID(), TourType.MORNING, java.util.UUID.randomUUID(), noRs))
                .isInstanceOf(com.albudoor.hms.platform.exception.DomainException.class);
    }
}
