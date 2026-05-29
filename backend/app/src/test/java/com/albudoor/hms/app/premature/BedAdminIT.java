package com.albudoor.hms.app.premature;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.premature.createbed.CreateBedCommand;
import com.albudoor.hms.premature.createbed.CreateBedHandler;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.domain.BedStatus;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import com.albudoor.hms.premature.updatebed.UpdateBedCommand;
import com.albudoor.hms.premature.updatebed.UpdateBedHandler;
import com.albudoor.hms.platform.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BedAdminIT extends IntegrationTest {

    @Autowired CreateBedHandler createBed;
    @Autowired UpdateBedHandler updateBed;
    @Autowired BedRepository beds;

    @Test
    void seeded_beds_exist_and_are_available() {
        assertThat(beds.findByCode("PREM-01")).isPresent();
        assertThat(beds.findByCode("PREM-01").get().getStatus()).isEqualTo(BedStatus.AVAILABLE);
    }

    @Test
    void create_then_update_bed() {
        String code = "PREM-IT-" + System.nanoTime();
        Bed created = createBed.handle(new CreateBedCommand(code, "Room Z"));
        assertThat(created.getStatus()).isEqualTo(BedStatus.AVAILABLE);

        Bed updated = updateBed.handle(created.getId(), new UpdateBedCommand("Room Y", false));
        assertThat(updated.getRoom()).isEqualTo("Room Y");
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    void duplicate_bed_code_is_rejected() {
        String code = "PREM-DUP-" + System.nanoTime();
        createBed.handle(new CreateBedCommand(code, null));
        assertThatThrownBy(() -> createBed.handle(new CreateBedCommand(code, null)))
                .isInstanceOf(ConflictException.class);
    }
}
