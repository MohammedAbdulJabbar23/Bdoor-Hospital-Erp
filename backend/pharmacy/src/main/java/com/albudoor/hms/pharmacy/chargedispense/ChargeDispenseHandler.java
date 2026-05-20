package com.albudoor.hms.pharmacy.chargedispense;

import com.albudoor.hms.cashier.createpayment.CreatePaymentCommand;
import com.albudoor.hms.cashier.createpayment.CreatePaymentHandler;
import com.albudoor.hms.cashier.domain.Payment;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.pharmacy.domain.DispenseLine;
import com.albudoor.hms.pharmacy.domain.PharmacyDispense;
import com.albudoor.hms.pharmacy.infrastructure.PharmacyDispenseRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hands a pharmacy dispense to the cashier: creates a {@code PHARMACY}-stage payment for
 * the billable drug lines and parks the dispense in {@code AWAITING_PAYMENT}. The
 * downstream {@link com.albudoor.hms.pharmacy.bridge.PaymentToDispenseBridge} advances the
 * dispense to {@code READY_TO_GIVE} when the cashier approves (or back to {@code PENDING}
 * if rejected).
 *
 * <p>Calling {@code charge} on a dispense with zero billable lines is rejected — there's
 * nothing to bill, so cancel instead.
 */
@Service
public class ChargeDispenseHandler {

    private final PharmacyDispenseRepository dispenses;
    private final CreatePaymentHandler createPayment;
    private final ApplicationEventPublisher events;

    public ChargeDispenseHandler(
            PharmacyDispenseRepository dispenses,
            CreatePaymentHandler createPayment,
            ApplicationEventPublisher events
    ) {
        this.dispenses = dispenses;
        this.createPayment = createPayment;
        this.events = events;
    }

    @Transactional
    public PharmacyDispense handle(UUID dispenseId) {
        PharmacyDispense dispense = dispenses.findById(dispenseId)
                .orElseThrow(() -> new NotFoundException("Pharmacy dispense not found: " + dispenseId));

        List<CreatePaymentCommand.Line> paymentLines = new ArrayList<>();
        for (DispenseLine l : dispense.getLines()) {
            if (l.isBillable()) {
                paymentLines.add(new CreatePaymentCommand.Line(l.getDrugServiceItemId(), l.getQuantity()));
            }
        }
        if (paymentLines.isEmpty()) {
            throw new DomainException("DISPENSE_NOTHING_TO_BILL",
                    "This dispense has no billable drug lines — cancel it instead");
        }

        // Charge through the cashier slice. CreatePaymentHandler emits its own
        // PaymentCreatedEvent (and PaymentApprovedEvent if VIP fast-path), so no extra
        // event work is needed here.
        Payment payment = createPayment.handle(new CreatePaymentCommand(
                dispense.getVisitId(),
                PaymentStage.PHARMACY,
                paymentLines,
                null /* default currency */
        ));

        dispense.charge(payment.getId());
        PharmacyDispense saved = dispenses.save(dispense);
        // Pull events from the source reference; merge() returns a different managed copy.
        dispense.pullDomainEvents().forEach(events::publishEvent);
        return saved;
    }
}
