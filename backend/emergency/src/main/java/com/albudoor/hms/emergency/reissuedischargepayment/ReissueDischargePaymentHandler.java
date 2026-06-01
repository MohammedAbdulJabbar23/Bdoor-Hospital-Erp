package com.albudoor.hms.emergency.reissuedischargepayment;

import com.albudoor.hms.cashier.createpayment.CreatePaymentCommand;
import com.albudoor.hms.cashier.createpayment.CreatePaymentHandler;
import com.albudoor.hms.cashier.domain.Payment;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.catalogue.domain.ServiceItem;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.emergency.domain.EmergencyCase;
import com.albudoor.hms.emergency.domain.EmergencyServiceCodes;
import com.albudoor.hms.emergency.infrastructure.EmergencyCaseRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Re-issues a discharge (FINAL) payment after a prior one was rejected (BRD P12b).
 *
 * <p>The visit is already at AWAITING_FINAL_PAYMENT (a FINAL rejection is an intentional
 * no-op for EMERGENCY visits), so this slice only mints a fresh FINAL payment and re-links
 * it to the case — it does NOT transition the visit.
 */
@Service("emergencyReissueDischargePaymentHandler")
public class ReissueDischargePaymentHandler {

    static final String DISCHARGE_ITEM_CODE = EmergencyServiceCodes.DISCHARGE;

    private final EmergencyCaseRepository cases;
    private final ServiceItemRepository catalogue;
    private final CreatePaymentHandler createPayment;

    public ReissueDischargePaymentHandler(EmergencyCaseRepository cases,
                                          ServiceItemRepository catalogue,
                                          CreatePaymentHandler createPayment) {
        this.cases = cases;
        this.catalogue = catalogue;
        this.createPayment = createPayment;
    }

    @Transactional
    public EmergencyCase handle(UUID caseId) {
        EmergencyCase ec = cases.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Case not found: " + caseId));

        ServiceItem dischargeFee = catalogue
                .findByCategoryAndCode(ServiceCategory.EMERGENCY, DISCHARGE_ITEM_CODE)
                .orElseThrow(() -> new DomainException("DISCHARGE_FEE_MISSING",
                        "Catalogue item " + DISCHARGE_ITEM_CODE + " is not configured"));
        Payment payment = createPayment.handle(new CreatePaymentCommand(
                ec.getVisitId(), PaymentStage.FINAL,
                List.of(new CreatePaymentCommand.Line(dischargeFee.getId(), 1)), null));

        ec.reissueDischargePayment(payment.getId()); // stays AWAITING_DISCHARGE_PAYMENT
        return cases.save(ec);
    }
}
