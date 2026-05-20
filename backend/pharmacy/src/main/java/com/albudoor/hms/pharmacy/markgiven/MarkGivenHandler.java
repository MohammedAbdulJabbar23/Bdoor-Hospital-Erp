package com.albudoor.hms.pharmacy.markgiven;

import com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal;
import com.albudoor.hms.pharmacy.domain.DispenseLine;
import com.albudoor.hms.pharmacy.domain.DrugBatch;
import com.albudoor.hms.pharmacy.domain.PharmacyDispense;
import com.albudoor.hms.pharmacy.infrastructure.DrugBatchRepository;
import com.albudoor.hms.pharmacy.infrastructure.PharmacyDispenseRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class MarkGivenHandler {

    private static final Logger log = LoggerFactory.getLogger(MarkGivenHandler.class);

    private final PharmacyDispenseRepository repo;
    private final DrugBatchRepository batches;
    private final ApplicationEventPublisher events;

    public MarkGivenHandler(
            PharmacyDispenseRepository repo,
            DrugBatchRepository batches,
            ApplicationEventPublisher events
    ) {
        this.repo = repo;
        this.batches = batches;
        this.events = events;
    }

    @Transactional
    public PharmacyDispense handle(UUID id) {
        PharmacyDispense d = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Pharmacy dispense not found: " + id));

        // Pre-flight stock check + decrement using FEFO. Only catalogue-priced lines consume
        // stock; free-text lines are skipped (no batch to draw from).
        LocalDate today = LocalDate.now();
        for (DispenseLine line : d.getLines()) {
            if (line.getDrugServiceItemId() == null) continue;
            int wanted = Math.max(1, line.getQuantity());
            List<DrugBatch> available = batches.findAvailableForDrug(line.getDrugServiceItemId(), today);
            int total = available.stream().mapToInt(DrugBatch::getQtyRemaining).sum();
            if (total < wanted) {
                throw new DomainException("OUT_OF_STOCK",
                        "Insufficient stock for " + line.getDrugName()
                                + " — need " + wanted + ", have " + total);
            }
            int remaining = wanted;
            for (DrugBatch b : available) {
                if (remaining <= 0) break;
                int drawn = b.draw(remaining);
                remaining -= drawn;
                batches.save(b);
                log.info("Drew {} of {} from batch {} (exp {})",
                        drawn, line.getDrugName(), b.getBatchNo(), b.getExpiryDate());
            }
        }

        d.markGiven(currentUserId());
        PharmacyDispense saved = repo.save(d);
        d.pullDomainEvents().forEach(events::publishEvent);
        return saved;
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HmsUserPrincipal p) {
            return p.userId();
        }
        return null;
    }
}
