package com.albudoor.hms.emergency.createbed;

import com.albudoor.hms.platform.exception.ConflictException;
import com.albudoor.hms.emergency.domain.EmergencyBed;
import com.albudoor.hms.emergency.infrastructure.EmergencyBedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("emergencyCreateBedHandler")
public class CreateBedHandler {

    private final EmergencyBedRepository beds;

    public CreateBedHandler(EmergencyBedRepository beds) {
        this.beds = beds;
    }

    @Transactional
    public EmergencyBed handle(CreateBedCommand cmd) {
        if (beds.existsByCode(cmd.code().trim())) {
            throw new ConflictException("BED_CODE_TAKEN", "Bed code already exists: " + cmd.code());
        }
        return beds.save(EmergencyBed.create(cmd.code(), cmd.room()));
    }
}
