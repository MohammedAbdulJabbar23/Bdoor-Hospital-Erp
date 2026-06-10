package com.albudoor.hms.bedstayforms.domain;

import com.albudoor.hms.platform.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TreatmentChartTest {

    private TreatmentChart chart() {
        return TreatmentChart.create(StayDepartment.PREMATURE, UUID.randomUUID(), LocalDate.of(2026, 6, 10));
    }

    @Test
    void replaceRows_replaces_the_whole_row_set() {
        TreatmentChart c = chart();
        c.replaceRows(List.of(
                new TreatmentRow("Ampicillin", "50mg/kg", "q12h", "08", null, null, null, null, "20"),
                new TreatmentRow("Gentamicin", "4mg/kg", "q24h", "10", null, null, null, null, null)));
        assertThat(c.getRows()).hasSize(2);

        c.replaceRows(List.of(new TreatmentRow("Caffeine citrate", "5mg/kg", "q24h", null, null, null, null, null, null)));
        assertThat(c.getRows()).hasSize(1);
        assertThat(c.getRows().get(0).getMedicineName()).isEqualTo("Caffeine citrate");
    }

    @Test
    void replaceRows_rejects_blank_medicine_name() {
        assertThatThrownBy(() -> chart().replaceRows(
                List.of(new TreatmentRow("  ", null, null, null, null, null, null, null, null))))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void create_requires_chart_date() {
        assertThatThrownBy(() -> TreatmentChart.create(StayDepartment.PREMATURE, UUID.randomUUID(), null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void doctor_signature_starts_empty_and_can_be_applied() {
        TreatmentChart c = chart();
        assertThat(c.getDoctorSignature().present()).isFalse();
        c.applyDoctorSignature("key-1", "Dr. A", UUID.randomUUID());
        assertThat(c.getDoctorSignature().present()).isTrue();
        assertThat(c.getDoctorSignature().getSignerName()).isEqualTo("Dr. A");
    }
}
