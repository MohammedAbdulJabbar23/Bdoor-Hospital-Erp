package com.albudoor.hms.bedstayforms.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MedicalHistorySheetTest {

    @Test
    void update_sets_all_fields_and_signatures_apply_per_slot() {
        MedicalHistorySheet s = MedicalHistorySheet.create(StayDepartment.EMERGENCY, UUID.randomUUID());
        s.update(new MedicalHistoryData(
                new BigDecimal("3.2"), new BigDecimal("49"), "Dr. B",
                "Fever 2 days", "Started gradually", "None", "Neonatal jaundice",
                "Diabetes (mother)", "No known allergies",
                "No", "No", "Normal",
                "None", "Chest clear, abdomen soft"));
        assertThat(s.getChiefComplaint()).isEqualTo("Fever 2 days");
        assertThat(s.getWeightKg()).isEqualByComparingTo("3.2");
        assertThat(s.getSocialSleep()).isEqualTo("Normal");

        assertThat(s.signature(MhSignatureSlot.SPECIALIST).present()).isFalse();
        s.applySignature(MhSignatureSlot.SPECIALIST, "k1", "Dr. Spec", UUID.randomUUID());
        assertThat(s.signature(MhSignatureSlot.SPECIALIST).present()).isTrue();
        assertThat(s.signature(MhSignatureSlot.PERMANENT).present()).isFalse();
    }
}
