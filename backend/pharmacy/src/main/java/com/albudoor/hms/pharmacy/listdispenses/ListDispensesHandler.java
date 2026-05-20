package com.albudoor.hms.pharmacy.listdispenses;

import com.albudoor.hms.pharmacy.domain.DispenseStatus;
import com.albudoor.hms.pharmacy.domain.PharmacyDispense;
import com.albudoor.hms.pharmacy.infrastructure.PharmacyDispenseRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ListDispensesHandler {

    private final PharmacyDispenseRepository repo;

    public ListDispensesHandler(PharmacyDispenseRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Page<PharmacyDispense> search(DispenseStatus status, int page, int size) {
        return repo.search(status, PageRequest.of(page, Math.min(size, 200)));
    }

    @Transactional(readOnly = true)
    public PharmacyDispense byId(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Pharmacy dispense not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<PharmacyDispense> byPatient(UUID patientId) {
        return repo.findAllByPatientIdOrderByCreatedAtDesc(patientId);
    }
}
