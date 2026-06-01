package com.albudoor.hms.app.emergency;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.emergency.createbed.CreateBedCommand;
import com.albudoor.hms.emergency.createbed.CreateBedHandler;
import com.albudoor.hms.emergency.domain.BedStatus;
import com.albudoor.hms.emergency.domain.EmergencyBed;
import com.albudoor.hms.emergency.infrastructure.EmergencyBedRepository;
import com.albudoor.hms.emergency.updatebed.UpdateBedCommand;
import com.albudoor.hms.emergency.updatebed.UpdateBedHandler;
import com.albudoor.hms.platform.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BedAdminIT extends IntegrationTest {

    @Autowired CreateBedHandler createBed;
    @Autowired UpdateBedHandler updateBed;
    @Autowired EmergencyBedRepository beds;

    @Test
    void seeded_beds_exist() {
        // V021 seeds EMRG-01..EMRG-08. Status is volatile across a shared test DB, so assert presence only.
        assertThat(beds.findByCode("EMRG-01")).isPresent();
    }

    @Test
    void create_then_update_bed() {
        String code = "EMRG-IT-" + System.nanoTime();
        EmergencyBed created = createBed.handle(new CreateBedCommand(code, "Room Z"));
        assertThat(created.getStatus()).isEqualTo(BedStatus.AVAILABLE);

        EmergencyBed updated = updateBed.handle(created.getId(), new UpdateBedCommand("Room Y", false));
        assertThat(updated.getRoom()).isEqualTo("Room Y");
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    void duplicate_bed_code_is_rejected() {
        String code = "EMRG-DUP-" + System.nanoTime();
        createBed.handle(new CreateBedCommand(code, null));
        assertThatThrownBy(() -> createBed.handle(new CreateBedCommand(code, null)))
                .isInstanceOf(ConflictException.class);
    }
}
