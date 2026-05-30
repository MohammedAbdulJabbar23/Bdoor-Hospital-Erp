package com.albudoor.hms.premature.getcase;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class DeriveAgeTest {
    @Test
    void days_then_weeks() {
        assertThat(GetCaseHandler.deriveAge(LocalDate.of(2026,5,1), LocalDate.of(2026,5,2))).isEqualTo("1 day");
        assertThat(GetCaseHandler.deriveAge(LocalDate.of(2026,5,1), LocalDate.of(2026,5,6))).isEqualTo("5 days");
        assertThat(GetCaseHandler.deriveAge(LocalDate.of(2026,5,1), LocalDate.of(2026,5,22))).isEqualTo("3 weeks");
        assertThat(GetCaseHandler.deriveAge(null, LocalDate.of(2026,5,2))).isNull();
    }
}
