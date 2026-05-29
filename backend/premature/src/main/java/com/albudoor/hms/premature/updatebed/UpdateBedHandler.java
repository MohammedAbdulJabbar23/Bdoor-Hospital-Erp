package com.albudoor.hms.premature.updatebed;

import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateBedHandler {

    private final BedRepository beds;

    public UpdateBedHandler(BedRepository beds) {
        this.beds = beds;
    }

    @Transactional
    public Bed handle(UUID id, UpdateBedCommand cmd) {
        Bed bed = beds.findById(id)
                .orElseThrow(() -> new NotFoundException("Bed not found: " + id));
        bed.updateDetails(cmd.room(), cmd.active());
        return beds.save(bed);
    }
}
