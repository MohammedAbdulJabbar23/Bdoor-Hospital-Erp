package com.albudoor.hms.pharmacy.canceldispense;

import com.albudoor.hms.pharmacy.domain.PharmacyDispense;
import com.albudoor.hms.pharmacy.infrastructure.PharmacyDispenseRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CancelDispenseHandler {

    private final PharmacyDispenseRepository repo;

    public CancelDispenseHandler(PharmacyDispenseRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public PharmacyDispense handle(UUID id, CancelDispenseCommand cmd) {
        PharmacyDispense d = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Pharmacy dispense not found: " + id));
        d.cancel(cmd.reason());
        return repo.save(d);
    }
}
