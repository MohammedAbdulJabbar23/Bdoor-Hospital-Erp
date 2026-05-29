package com.albudoor.hms.premature.domain;

import com.albudoor.hms.platform.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BedTest {

    @Test
    void created_bed_is_available_and_active() {
        Bed bed = Bed.create("PREM-09", "Room C");
        assertThat(bed.getCode()).isEqualTo("PREM-09");
        assertThat(bed.getStatus()).isEqualTo(BedStatus.AVAILABLE);
        assertThat(bed.isActive()).isTrue();
    }

    @Test
    void create_requires_code() {
        assertThatThrownBy(() -> Bed.create("  ", "Room C")).isInstanceOf(DomainException.class);
    }

    @Test
    void reserve_then_occupy_then_discharge_cycles_back_to_available() {
        Bed bed = Bed.create("PREM-09", null);
        bed.reserve();
        assertThat(bed.getStatus()).isEqualTo(BedStatus.PENDING_PAYMENT);
        bed.occupy();
        assertThat(bed.getStatus()).isEqualTo(BedStatus.OCCUPIED);
        bed.discharge();
        assertThat(bed.getStatus()).isEqualTo(BedStatus.AVAILABLE);
    }

    @Test
    void reserve_releases_back_to_available_on_rejection() {
        Bed bed = Bed.create("PREM-09", null);
        bed.reserve();
        bed.release();
        assertThat(bed.getStatus()).isEqualTo(BedStatus.AVAILABLE);
    }

    @Test
    void cannot_reserve_an_occupied_bed() {
        Bed bed = Bed.create("PREM-09", null);
        bed.reserve();
        bed.occupy();
        assertThatThrownBy(bed::reserve).isInstanceOf(DomainException.class);
    }

    @Test
    void cannot_reserve_an_inactive_bed() {
        Bed bed = Bed.create("PREM-09", null);
        bed.deactivate();
        assertThatThrownBy(bed::reserve).isInstanceOf(DomainException.class);
    }

    @Test
    void cannot_occupy_unless_pending_payment() {
        Bed bed = Bed.create("PREM-09", null);
        assertThatThrownBy(bed::occupy).isInstanceOf(DomainException.class);
    }
}
