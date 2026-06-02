package com.albudoor.hms.pharmacy.chargedispense;

import com.albudoor.hms.cashier.createpayment.CreatePaymentCommand;
import com.albudoor.hms.cashier.createpayment.CreatePaymentHandler;
import com.albudoor.hms.cashier.domain.Payment;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.pharmacy.domain.DispenseLine;
import com.albudoor.hms.pharmacy.domain.DrugBatch;
import com.albudoor.hms.pharmacy.domain.PharmacyDispense;
import com.albudoor.hms.pharmacy.infrastructure.DrugBatchRepository;
import com.albudoor.hms.pharmacy.infrastructure.PharmacyDispenseRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final DrugBatchRepository batches;
    private final CreatePaymentHandler createPayment;
    private final ApplicationEventPublisher events;

    public ChargeDispenseHandler(
            PharmacyDispenseRepository dispenses,
            DrugBatchRepository batches,
            CreatePaymentHandler createPayment,
            ApplicationEventPublisher events
    ) {
        this.dispenses = dispenses;
        this.batches = batches;
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

        // Stock check BEFORE charging: the FEFO mark-given path will draw these quantities,
        // so verify available (non-expired) stock now. Charging a patient for stock that
        // isn't there is the defect we're closing — fail fast with 422 OUT_OF_STOCK before
        // any payment is created. (mark-given keeps its own check + the actual decrement.)
        //
        // RESERVATION: stock isn't decremented until mark-given, so two concurrent in-flight
        // dispenses for the same drug could each pass a bare available-stock check yet only the
        // first could actually be handed over. We close that by treating already-in-flight
        // dispenses (charged/awaiting-payment + ready-to-give) as "committed" and requiring
        // available - committedByOthers >= wanted. Reject/cancel/give naturally drop a dispense
        // out of the committed set (no explicit release needed).
        LocalDate today = LocalDate.now();
        for (DispenseLine l : dispense.getLines()) {
            if (l.getDrugServiceItemId() == null) continue;
            int wanted = Math.max(1, l.getQuantity());
            List<DrugBatch> available = batches.findAvailableForDrug(l.getDrugServiceItemId(), today);
            int total = available.stream().mapToInt(DrugBatch::getQtyRemaining).sum();
            long committedByOthers =
                    dispenses.committedQtyForDrugExcluding(l.getDrugServiceItemId(), dispense.getId());
            long uncommitted = total - committedByOthers;
            if (uncommitted < wanted) {
                throw new DomainException("OUT_OF_STOCK",
                        "Insufficient stock for " + l.getDrugName()
                                + " — need " + wanted + ", have " + total
                                + (committedByOthers > 0
                                        ? " (" + committedByOthers + " reserved by in-flight dispenses)"
                                        : ""));
            }
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
