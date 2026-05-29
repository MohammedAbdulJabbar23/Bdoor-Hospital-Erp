package com.albudoor.hms.premature.createbed;

import com.albudoor.hms.platform.exception.ConflictException;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateBedHandler {

    private final BedRepository beds;

    public CreateBedHandler(BedRepository beds) {
        this.beds = beds;
    }

    @Transactional
    public Bed handle(CreateBedCommand cmd) {
        if (beds.existsByCode(cmd.code().trim())) {
            throw new ConflictException("BED_CODE_TAKEN", "Bed code already exists: " + cmd.code());
        }
        return beds.save(Bed.create(cmd.code(), cmd.room()));
    }
}
