package com.albudoor.hms.app;

import com.albudoor.hms.cashier.domain.PaymentApprovedEvent;
import com.albudoor.hms.cashier.domain.PaymentRejectedEvent;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cross-module wiring: when the cashier module emits a payment decision, advance the
 * visit's status accordingly. Lives in the composition root so neither module depends
 * on the other beyond the shared event contract.
 *
 * <p>Stage → transition map:
 * <ul>
 *   <li><b>INITIAL / REFERRAL</b> approved → {@code IN_PROGRESS}; rejected → no-op
 *       (calling module decides whether to cancel the visit).</li>
 *   <li><b>FINAL</b> approved → {@code COMPLETED}; rejected → {@code OUTSTANDING_BALANCE}.</li>
 *   <li><b>STAY_EXTENSION / PHARMACY</b> — payment is side-billing, visit state is unaffected.</li>
 * </ul>
 */
@Component
public class PaymentVisitBridge {

    private static final Logger log = LoggerFactory.getLogger(PaymentVisitBridge.class);

    private final VisitRepository visits;

    public PaymentVisitBridge(VisitRepository visits) {
        this.visits = visits;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApproved(PaymentApprovedEvent event) {
        if (event.stage() == PaymentStage.STAY_EXTENSION) return;

        Visit visit = visits.findById(event.visitId()).orElse(null);
        if (visit == null) {
            log.warn("PaymentApproved for unknown visit {}", event.visitId());
            return;
        }

        // PHARMACY-stage payments are side-bills for a doctor visit and must NOT change the
        // doctor visit's status. The single exception: an OTC walk-in sale has its own
        // PHARMACY-type anchor visit — that one DOES follow the dispense lifecycle.
        if (event.stage() == PaymentStage.PHARMACY
                && visit.getVisitType() != com.albudoor.hms.visitmanagement.domain.VisitType.PHARMACY) {
            return;
        }

        VisitStatus target = (event.stage() == PaymentStage.FINAL)
                ? VisitStatus.COMPLETED
                : VisitStatus.IN_PROGRESS;

        if (visit.getStatus() == target) return;

        // Initial / referral: only advance if the visit is parked at AWAITING_PAYMENT.
        // Final: only advance if parked at AWAITING_FINAL_PAYMENT.
        boolean atExpectedHold =
                (event.stage() == PaymentStage.FINAL && visit.getStatus() == VisitStatus.AWAITING_FINAL_PAYMENT)
                        || (event.stage() != PaymentStage.FINAL && visit.getStatus() == VisitStatus.AWAITING_PAYMENT);

        if (!atExpectedHold) {
            log.debug("Payment {} approved but visit {} is in {}; skipping auto-transition",
                    event.paymentId(), event.visitId(), visit.getStatus());
            return;
        }
        try {
            visit.transitionTo(target);
            log.info("Visit {} → {} after payment {} ({}stage={})",
                    visit.getVisitDisplayId(), target, event.paymentId(),
                    event.vipBypass() ? "VIP, " : "", event.stage());
        } catch (RuntimeException ex) {
            log.warn("Could not auto-transition visit {} to {}: {}",
                    visit.getId(), target, ex.getMessage());
        }
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRejected(PaymentRejectedEvent event) {
        if (event.stage() != PaymentStage.FINAL) return;

        Visit visit = visits.findById(event.visitId()).orElse(null);
        if (visit == null) return;
        if (visit.getStatus() != VisitStatus.AWAITING_FINAL_PAYMENT) return;
        try {
            visit.transitionTo(VisitStatus.OUTSTANDING_BALANCE);
            log.info("Visit {} → OUTSTANDING_BALANCE after final payment {} rejected",
                    visit.getVisitDisplayId(), event.paymentId());
        } catch (RuntimeException ex) {
            log.warn("Could not transition visit {} to OUTSTANDING_BALANCE: {}",
                    visit.getId(), ex.getMessage());
        }
    }
}
