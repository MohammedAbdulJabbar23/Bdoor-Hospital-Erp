package com.albudoor.hms.departmentservices.bridge;

import com.albudoor.hms.cashier.domain.PaymentApprovedEvent;
import com.albudoor.hms.departmentservices.domain.DepartmentCase;
import com.albudoor.hms.departmentservices.infrastructure.DepartmentCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for cashier {@code PaymentApprovedEvent} and advances the linked
 * {@link DepartmentCase} from {@code AWAITING_PAYMENT} to {@code AWAITING_STUDY}.
 *
 * <p>Lives in the dept-services module (not the app composition root) because the
 * dept module already depends on cashier; this keeps the wiring local to the bounded
 * context that owns the case lifecycle.
 */
@Component
public class PaymentToCaseBridge {

    private static final Logger log = LoggerFactory.getLogger(PaymentToCaseBridge.class);

    private final DepartmentCaseRepository cases;

    public PaymentToCaseBridge(DepartmentCaseRepository cases) {
        this.cases = cases;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApproved(PaymentApprovedEvent event) {
        cases.findByPaymentId(event.paymentId()).ifPresent(c -> {
            c.onPaymentApproved();
            log.info("DepartmentCase {} → AWAITING_STUDY (payment {} approved)",
                    c.getId(), event.paymentId());
        });
    }
}
