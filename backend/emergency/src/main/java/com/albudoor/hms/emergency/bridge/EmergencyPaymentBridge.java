package com.albudoor.hms.emergency.bridge;

import com.albudoor.hms.cashier.domain.PaymentApprovedEvent;
import com.albudoor.hms.cashier.domain.PaymentRejectedEvent;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.emergency.infrastructure.EmergencyBedRepository;
import com.albudoor.hms.emergency.infrastructure.EmergencyCaseRepository;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Drives the emergency bed-stay case off cashier payment decisions. The generic
 * PaymentVisitBridge already advances the VISIT (INITIAL->IN_PROGRESS, FINAL->COMPLETED);
 * this bridge advances the CASE + BED, and owns the emergency-specific rejection rules.
 *
 * INITIAL approved -> case UNDER_TREATMENT, bed OCCUPIED.
 * INITIAL rejected -> bed released, case CANCELLED, visit cancelled.
 *
 * FINAL approve/reject handling is added in Phase 7.
 */
@Component("emergencyPaymentBridge")
public class EmergencyPaymentBridge {

    private static final Logger log = LoggerFactory.getLogger(EmergencyPaymentBridge.class);

    private final EmergencyCaseRepository cases;
    private final EmergencyBedRepository beds;
    private final VisitRepository visits;

    public EmergencyPaymentBridge(EmergencyCaseRepository cases, EmergencyBedRepository beds,
                                  VisitRepository visits) {
        this.cases = cases;
        this.beds = beds;
        this.visits = visits;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApproved(PaymentApprovedEvent event) {
        if (event.stage() == PaymentStage.INITIAL) {
            cases.findByInitialPaymentId(event.paymentId()).ifPresent(ec -> {
                ec.markUnderTreatment();
                cases.save(ec);
                beds.findById(ec.getBedId()).ifPresent(bed -> {
                    bed.occupy();
                    beds.save(bed);
                });
                log.info("Emergency case {} -> UNDER_TREATMENT, bed {} OCCUPIED (initial payment {})",
                        ec.getId(), ec.getBedCode(), event.paymentId());
            });
        }
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRejected(PaymentRejectedEvent event) {
        if (event.stage() == PaymentStage.INITIAL) {
            cases.findByInitialPaymentId(event.paymentId()).ifPresent(ec -> {
                beds.findById(ec.getBedId()).ifPresent(bed -> {
                    bed.release();
                    beds.save(bed);
                });
                ec.cancel();
                cases.save(ec);
                Visit visit = visits.findById(ec.getVisitId()).orElse(null);
                if (visit != null && !visit.getStatus().isTerminal()) {
                    visit.cancel("Initial emergency payment rejected");
                    visits.save(visit);
                }
                log.info("Emergency case {} CANCELLED, bed {} released (initial payment {} rejected)",
                        ec.getId(), ec.getBedCode(), event.paymentId());
            });
        }
    }
}
