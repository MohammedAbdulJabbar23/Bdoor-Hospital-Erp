package com.albudoor.hms.bedstayforms.directory;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AgeTextTest {

    private static final LocalDate AT = LocalDate.of(2026, 6, 10);

    @Test
    void tiers_days_weeks_months_years() {
        assertThat(AgeText.derive(AT.minusDays(1), AT)).isEqualTo("1 day");
        assertThat(AgeText.derive(AT.minusDays(12), AT)).isEqualTo("12 days");
        assertThat(AgeText.derive(AT.minusDays(21), AT)).isEqualTo("3 weeks");
        assertThat(AgeText.derive(AT.minusMonths(7), AT)).isEqualTo("7 months");
        assertThat(AgeText.derive(AT.minusYears(34), AT)).isEqualTo("34 years");
    }

    @Test
    void invalid_inputs_return_null() {
        assertThat(AgeText.derive(null, AT)).isNull();
        assertThat(AgeText.derive(AT, null)).isNull();
        assertThat(AgeText.derive(AT.plusDays(1), AT)).isNull();
    }
}
