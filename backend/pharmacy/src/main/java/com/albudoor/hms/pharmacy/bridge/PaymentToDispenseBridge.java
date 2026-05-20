package com.albudoor.hms.pharmacy.bridge;

import com.albudoor.hms.cashier.domain.PaymentApprovedEvent;
import com.albudoor.hms.cashier.domain.PaymentRejectedEvent;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.pharmacy.domain.PharmacyDispense;
import com.albudoor.hms.pharmacy.infrastructure.PharmacyDispenseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for cashier decisions on PHARMACY-stage payments and advances the linked
 * {@link PharmacyDispense}.
 *
 * <ul>
 *   <li>Approved → {@code AWAITING_PAYMENT} → {@code READY_TO_GIVE}</li>
 *   <li>Rejected → {@code AWAITING_PAYMENT} → {@code PENDING} (pharmacist can re-charge or cancel)</li>
 * </ul>
 *
 * <p>Non-PHARMACY stage events are ignored — visits are billed for many things; only the
 * pharmacy slice cares about the drug stage.
 */
@Component
public class PaymentToDispenseBridge {

    private static final Logger log = LoggerFactory.getLogger(PaymentToDispenseBridge.class);

    private final PharmacyDispenseRepository dispenses;

    public PaymentToDispenseBridge(PharmacyDispenseRepository dispenses) {
        this.dispenses = dispenses;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApproved(PaymentApprovedEvent event) {
        if (event.stage() != PaymentStage.PHARMACY) return;
        dispenses.findByChargePaymentId(event.paymentId()).ifPresent(d -> {
            d.onPaymentApproved();
            log.info("Pharmacy dispense {} → READY_TO_GIVE (payment {} approved)",
                    d.getDispenseDisplayId(), event.paymentId());
        });
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRejected(PaymentRejectedEvent event) {
        if (event.stage() != PaymentStage.PHARMACY) return;
        dispenses.findByChargePaymentId(event.paymentId()).ifPresent(d -> {
            d.onPaymentRejected();
            log.info("Pharmacy dispense {} reverted to PENDING (payment {} rejected)",
                    d.getDispenseDisplayId(), event.paymentId());
        });
    }
}
