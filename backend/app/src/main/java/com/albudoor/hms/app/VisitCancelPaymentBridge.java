package com.albudoor.hms.app;

import com.albudoor.hms.cashier.domain.Payment;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.domain.VisitStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * When a visit is cancelled (e.g. receptionist cancels the appointment), reject any
 * still-pending payments tied to that visit so they disappear from the cashier queue.
 *
 * Addresses HMS punch-list item: "when cancelling an appointment the cashier request
 * is not cancelled".
 */
@Component
public class VisitCancelPaymentBridge {

    private static final Logger log = LoggerFactory.getLogger(VisitCancelPaymentBridge.class);

    private final PaymentRepository payments;

    public VisitCancelPaymentBridge(PaymentRepository payments) {
        this.payments = payments;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onVisitStatusChanged(VisitStatusChangedEvent event) {
        if (event.to() != VisitStatus.CANCELLED) return;

        var pendings = payments.findAllByVisitIdOrderByCreatedAtDesc(event.visitId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .toList();
        if (pendings.isEmpty()) return;

        for (Payment p : pendings) {
            // System-initiated rejection — no human cashier; cashierUserId stays null.
            p.reject("Visit cancelled", null);
            payments.save(p);
            log.info("Auto-rejected pending payment {} because visit {} was cancelled",
                    p.getId(), event.visitId());
        }
    }
}
