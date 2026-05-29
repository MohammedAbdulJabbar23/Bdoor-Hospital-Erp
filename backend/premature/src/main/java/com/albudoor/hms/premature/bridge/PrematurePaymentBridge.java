package com.albudoor.hms.premature.bridge;

import com.albudoor.hms.cashier.domain.PaymentApprovedEvent;
import com.albudoor.hms.cashier.domain.PaymentRejectedEvent;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Drives the premature bed-stay case off cashier payment decisions. The generic
 * PaymentVisitBridge already advances the VISIT (INITIAL->IN_PROGRESS, FINAL->COMPLETED);
 * this bridge advances the ADMISSION + BED, and owns the premature-specific rejection rules.
 *
 * INITIAL approved -> admission UNDER_CARE, bed OCCUPIED.
 * INITIAL rejected -> bed released, admission CANCELLED, visit cancelled.
 * FINAL approved -> admission CLOSED, bed discharged.
 * FINAL rejected -> intentional no-op: admission stays AWAITING_DISCHARGE_PAYMENT so the
 *                   case stays open and a fresh discharge payment can be re-issued (P12b).
 */
@Component
public class PrematurePaymentBridge {

    private static final Logger log = LoggerFactory.getLogger(PrematurePaymentBridge.class);

    private final PrematureAdmissionRepository admissions;
    private final BedRepository beds;
    private final VisitRepository visits;

    public PrematurePaymentBridge(PrematureAdmissionRepository admissions, BedRepository beds,
                                  VisitRepository visits) {
        this.admissions = admissions;
        this.beds = beds;
        this.visits = visits;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApproved(PaymentApprovedEvent event) {
        if (event.stage() == PaymentStage.INITIAL) {
            admissions.findByInitialPaymentId(event.paymentId()).ifPresent(admission -> {
                admission.markUnderCare();
                admissions.save(admission);
                beds.findById(admission.getBedId()).ifPresent(bed -> {
                    bed.occupy();
                    beds.save(bed);
                });
                log.info("Premature admission {} -> UNDER_CARE, bed {} OCCUPIED (initial payment {})",
                        admission.getId(), admission.getBedCode(), event.paymentId());
            });
        }
        if (event.stage() == PaymentStage.FINAL) {
            admissions.findByFinalPaymentId(event.paymentId()).ifPresent(admission -> {
                admission.close();
                admissions.save(admission);
                beds.findById(admission.getBedId()).ifPresent(bed -> {
                    bed.discharge();
                    beds.save(bed);
                });
                log.info("Premature admission {} CLOSED, bed {} discharged (final payment {})",
                        admission.getId(), admission.getBedCode(), event.paymentId());
            });
        }
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRejected(PaymentRejectedEvent event) {
        if (event.stage() == PaymentStage.INITIAL) {
            admissions.findByInitialPaymentId(event.paymentId()).ifPresent(admission -> {
                beds.findById(admission.getBedId()).ifPresent(bed -> {
                    bed.release();
                    beds.save(bed);
                });
                admission.cancel();
                admissions.save(admission);
                Visit visit = visits.findById(admission.getVisitId()).orElse(null);
                if (visit != null && !visit.getStatus().isTerminal()) {
                    visit.cancel("Initial premature payment rejected");
                    visits.save(visit);
                }
                log.info("Premature admission {} CANCELLED, bed {} released (initial payment {} rejected)",
                        admission.getId(), admission.getBedCode(), event.paymentId());
            });
        }
        // FINAL rejection (P12b): the generic PaymentVisitBridge is guarded to skip PREMATURE
        // visits, so the visit stays AWAITING_FINAL_PAYMENT and the admission stays
        // AWAITING_DISCHARGE_PAYMENT — the case remains open and a fresh FINAL payment can be
        // issued via the reissue-discharge-payment endpoint. No state change here by design.
    }
}
