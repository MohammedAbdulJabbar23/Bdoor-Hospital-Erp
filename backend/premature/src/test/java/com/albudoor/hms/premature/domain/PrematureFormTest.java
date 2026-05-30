package com.albudoor.hms.premature.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PrematureFormTest {

    private PrematureFormData data() {
        return new PrematureFormData(
                "12 days", new BigDecimal("1.200"), null, new BigDecimal("1.450"), null,
                32, 4, 34, 1, new BigDecimal("42.0"), null, new BigDecimal("30.0"), null,
                "EBM", new BigDecimal("20"), new BigDecimal("150"), new BigDecimal("110"),
                new BigDecimal("6.0"), "notes", null, "Blood", "No growth", "Rx text", "spec notes");
    }

    @Test
    void create_then_update_holds_fields() {
        PrematureForm f = PrematureForm.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        f.update(data());
        assertThat(f.getCurrentWeightKg()).isEqualByComparingTo("1.450");
        assertThat(f.getCorrectedGaWeeks()).isEqualTo(34);
        assertThat(f.getFeedingType()).isEqualTo("EBM");
        assertThat(f.getGir()).isEqualByComparingTo("6.0");
    }

    @Test
    void apply_signature_sets_slot_with_metadata() {
        PrematureForm f = PrematureForm.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        UUID user = UUID.randomUUID();
        f.applySignature(SignatureSlot.RESIDENT, "2026-05-30/abc.png", "Dr. Noor", user);
        assertThat(f.getResidentSignature()).isNotNull();
        assertThat(f.getResidentSignature().getImageKey()).isEqualTo("2026-05-30/abc.png");
        assertThat(f.getResidentSignature().getSignerName()).isEqualTo("Dr. Noor");
        assertThat(f.getResidentSignature().getSignedBy()).isEqualTo(user);
        assertThat(f.getResidentSignature().getSignedAt()).isNotNull();
        assertThat(f.getClinicalPharmacySignature().getImageKey()).isNull();
    }
}
