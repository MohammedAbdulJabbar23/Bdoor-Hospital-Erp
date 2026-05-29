package com.albudoor.hms.premature.reissuedischargepayment;

import com.albudoor.hms.cashier.createpayment.CreatePaymentCommand;
import com.albudoor.hms.cashier.createpayment.CreatePaymentHandler;
import com.albudoor.hms.cashier.domain.Payment;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.catalogue.domain.ServiceItem;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Re-issues a discharge (FINAL) payment after a prior one was rejected (BRD P12b).
 *
 * <p>The visit is already at AWAITING_FINAL_PAYMENT (a FINAL rejection is an intentional
 * no-op for PREMATURE visits), so this slice only mints a fresh FINAL payment and re-links
 * it to the admission — it does NOT transition the visit.
 */
@Service
public class ReissueDischargePaymentHandler {

    static final String DISCHARGE_ITEM_CODE = "PREM-DIS";

    private final PrematureAdmissionRepository admissions;
    private final ServiceItemRepository catalogue;
    private final CreatePaymentHandler createPayment;

    public ReissueDischargePaymentHandler(PrematureAdmissionRepository admissions,
                                          ServiceItemRepository catalogue,
                                          CreatePaymentHandler createPayment) {
        this.admissions = admissions;
        this.catalogue = catalogue;
        this.createPayment = createPayment;
    }

    @Transactional
    public PrematureAdmission handle(UUID admissionId) {
        PrematureAdmission admission = admissions.findById(admissionId)
                .orElseThrow(() -> new NotFoundException("Admission not found: " + admissionId));

        ServiceItem dischargeFee = catalogue
                .findByCategoryAndCode(ServiceCategory.PREMATURE, DISCHARGE_ITEM_CODE)
                .orElseThrow(() -> new DomainException("DISCHARGE_FEE_MISSING",
                        "Catalogue item " + DISCHARGE_ITEM_CODE + " is not configured"));
        Payment payment = createPayment.handle(new CreatePaymentCommand(
                admission.getVisitId(), PaymentStage.FINAL,
                List.of(new CreatePaymentCommand.Line(dischargeFee.getId(), 1)), null));

        admission.reissueDischargePayment(payment.getId()); // stays AWAITING_DISCHARGE_PAYMENT
        return admissions.save(admission);
    }
}
