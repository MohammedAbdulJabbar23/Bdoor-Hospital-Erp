package com.albudoor.hms.bedstayforms.domain;

import com.albudoor.hms.platform.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NursingProcedureEntryTest {

    private static final UUID STAY = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-06-10T08:00:00Z");

    @Test
    void record_trims_name_and_attributes_the_nurse() {
        UUID nurse = UUID.randomUUID();
        NursingProcedureEntry e = NursingProcedureEntry.record(
                StayDepartment.PREMATURE, STAY, "  Umbilical care  ", AT, "ok", "Nurse Amal", nurse);
        assertThat(e.getProcedureName()).isEqualTo("Umbilical care");
        assertThat(e.getPerformedAt()).isEqualTo(AT);
        assertThat(e.getNurseName()).isEqualTo("Nurse Amal");
        assertThat(e.getRecordedBy()).isEqualTo(nurse);
        assertThat(e.getId()).isNotNull();
    }

    @Test
    void record_rejects_blank_procedure_name() {
        assertThatThrownBy(() -> NursingProcedureEntry.record(
                StayDepartment.PREMATURE, STAY, "   ", AT, null, null, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void record_requires_performed_at_and_stay_refs() {
        assertThatThrownBy(() -> NursingProcedureEntry.record(
                StayDepartment.PREMATURE, STAY, "Feeding", null, null, null, null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> NursingProcedureEntry.record(
                null, STAY, "Feeding", AT, null, null, null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> NursingProcedureEntry.record(
                StayDepartment.PREMATURE, null, "Feeding", AT, null, null, null))
                .isInstanceOf(DomainException.class);
    }
}
