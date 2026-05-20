package com.albudoor.hms.app;

import com.albudoor.hms.pharmacy.domain.DispenseGivenEvent;
import com.albudoor.hms.pharmacy.infrastructure.PharmacyDispenseRepository;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * When a PHARMACY-anchor visit's dispense is marked given, complete the visit so it leaves
 * the active queue. Prescription-flow dispenses (anchored to a DOCTOR_APPOINTMENT visit)
 * do not touch the parent visit — that visit closes when the doctor's exam finalizes.
 */
@Component
public class OtcVisitCompletionBridge {

    private static final Logger log = LoggerFactory.getLogger(OtcVisitCompletionBridge.class);

    private final PharmacyDispenseRepository dispenses;
    private final VisitRepository visits;

    public OtcVisitCompletionBridge(PharmacyDispenseRepository dispenses, VisitRepository visits) {
        this.dispenses = dispenses;
        this.visits = visits;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDispenseGiven(DispenseGivenEvent event) {
        var d = dispenses.findById(event.dispenseId()).orElse(null);
        if (d == null || d.getVisitId() == null) return;
        Visit v = visits.findById(d.getVisitId()).orElse(null);
        if (v == null || v.getVisitType() != VisitType.PHARMACY) return;
        if (v.getStatus().isTerminal()) return;
        try {
            v.transitionTo(VisitStatus.COMPLETED);
            log.info("OTC anchor visit {} → COMPLETED after dispense {} given",
                    v.getVisitDisplayId(), d.getDispenseDisplayId());
        } catch (RuntimeException ex) {
            log.warn("Could not complete OTC visit {}: {}", v.getId(), ex.getMessage());
        }
    }
}
