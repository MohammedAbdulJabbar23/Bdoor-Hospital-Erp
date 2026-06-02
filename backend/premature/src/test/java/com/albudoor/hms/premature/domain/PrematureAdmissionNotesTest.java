package com.albudoor.hms.premature.domain;

import org.junit.jupiter.api.Test;
import com.albudoor.hms.platform.exception.DomainException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PrematureAdmissionNotesTest {
    private PrematureAdmission open() {
        return PrematureAdmission.open(UUID.randomUUID(), "V-1", UUID.randomUUID(), "MRN", "Pat",
                UUID.randomUUID(), "BED-1", 3, StayUnit.DAYS);
    }
    @Test void setDischargeNote_persistsText() {
        PrematureAdmission a = open();
        a.setDischargeNote("Stable. Home on oral feeds. Review in 1 week.");
        assertThat(a.getDischargeNote()).contains("Stable");
    }
    @Test void recordFinishOverride_requiresReason() {
        PrematureAdmission a = open();
        assertThatThrownBy(() -> a.recordFinishOverride("  "))
                .isInstanceOf(DomainException.class);
        a.recordFinishOverride("Parents accept results will follow");
        assertThat(a.getFinishOverrideReason()).isNotBlank();
    }
}
