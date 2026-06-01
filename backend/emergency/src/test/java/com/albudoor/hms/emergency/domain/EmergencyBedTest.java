package com.albudoor.hms.emergency.domain;

import com.albudoor.hms.platform.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmergencyBedTest {

    @Test
    void created_bed_is_available_and_active() {
        EmergencyBed bed = EmergencyBed.create("EMRG-09", "Room C");
        assertThat(bed.getCode()).isEqualTo("EMRG-09");
        assertThat(bed.getStatus()).isEqualTo(BedStatus.AVAILABLE);
        assertThat(bed.isActive()).isTrue();
    }

    @Test
    void create_requires_code() {
        assertThatThrownBy(() -> EmergencyBed.create("  ", "Room C")).isInstanceOf(DomainException.class);
    }

    @Test
    void reserve_then_occupy_then_discharge_cycles_back_to_available() {
        EmergencyBed bed = EmergencyBed.create("EMRG-09", null);
        bed.reserve();
        assertThat(bed.getStatus()).isEqualTo(BedStatus.PENDING_PAYMENT);
        bed.occupy();
        assertThat(bed.getStatus()).isEqualTo(BedStatus.OCCUPIED);
        bed.discharge();
        assertThat(bed.getStatus()).isEqualTo(BedStatus.AVAILABLE);
    }

    @Test
    void reserve_releases_back_to_available_on_rejection() {
        EmergencyBed bed = EmergencyBed.create("EMRG-09", null);
        bed.reserve();
        bed.release();
        assertThat(bed.getStatus()).isEqualTo(BedStatus.AVAILABLE);
    }

    @Test
    void cannot_reserve_an_occupied_bed() {
        EmergencyBed bed = EmergencyBed.create("EMRG-09", null);
        bed.reserve();
        bed.occupy();
        assertThatThrownBy(bed::reserve).isInstanceOf(DomainException.class);
    }

    @Test
    void cannot_reserve_an_inactive_bed() {
        EmergencyBed bed = EmergencyBed.create("EMRG-09", null);
        bed.deactivate();
        assertThatThrownBy(bed::reserve).isInstanceOf(DomainException.class);
    }

    @Test
    void cannot_occupy_unless_pending_payment() {
        EmergencyBed bed = EmergencyBed.create("EMRG-09", null);
        assertThatThrownBy(bed::occupy).isInstanceOf(DomainException.class);
    }
}
