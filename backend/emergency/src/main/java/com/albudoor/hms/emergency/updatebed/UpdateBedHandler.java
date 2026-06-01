package com.albudoor.hms.emergency.updatebed;

import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.emergency.domain.EmergencyBed;
import com.albudoor.hms.emergency.infrastructure.EmergencyBedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service("emergencyUpdateBedHandler")
public class UpdateBedHandler {

    private final EmergencyBedRepository beds;

    public UpdateBedHandler(EmergencyBedRepository beds) {
        this.beds = beds;
    }

    @Transactional
    public EmergencyBed handle(UUID id, UpdateBedCommand cmd) {
        EmergencyBed bed = beds.findById(id)
                .orElseThrow(() -> new NotFoundException("Bed not found: " + id));
        bed.updateDetails(cmd.room(), cmd.active());
        return beds.save(bed);
    }
}
