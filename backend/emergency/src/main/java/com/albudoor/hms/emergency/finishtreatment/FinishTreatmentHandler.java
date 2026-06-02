package com.albudoor.hms.emergency.finishtreatment;

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
import com.albudoor.hms.platform.exception.ResultsPendingException;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service("emergencyFinishTreatmentHandler")
public class FinishTreatmentHandler {

    static final String DISCHARGE_ITEM_CODE = EmergencyServiceCodes.DISCHARGE;

    private final EmergencyCaseRepository cases;
    private final VisitRepository visits;
    private final ServiceItemRepository catalogue;
    private final CreatePaymentHandler createPayment;
    private final ApplicationEventPublisher events;

    public FinishTreatmentHandler(EmergencyCaseRepository cases, VisitRepository visits,
                                  ServiceItemRepository catalogue, CreatePaymentHandler createPayment,
                                  ApplicationEventPublisher events) {
        this.cases = cases;
        this.visits = visits;
        this.catalogue = catalogue;
        this.createPayment = createPayment;
        this.events = events;
    }

    @Transactional
    public EmergencyCase handle(UUID caseId, FinishTreatmentCommand cmd) {
        EmergencyCase ec = cases.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Case not found: " + caseId));

        // Results-pending gate (warn + override): open = child visit not COMPLETED/CANCELLED.
        List<Visit> children = visits.findAllByParentVisitIdOrderByStartedAtDesc(ec.getVisitId());
        List<Visit> open = children.stream()
                .filter(v -> v.getStatus() != VisitStatus.COMPLETED && v.getStatus() != VisitStatus.CANCELLED)
                .toList();
        if (!open.isEmpty()) {
            if (!cmd.override()) {
                String list = open.stream()
                        .map(v -> v.getVisitDisplayId() + " (" + v.getVisitType() + ", " + v.getStatus() + ")")
                        .collect(java.util.stream.Collectors.joining(", "));
                throw new ResultsPendingException("Results still pending: " + list);
            }
            ec.recordFinishOverride(cmd.overrideReason()); // throws if reason blank
        }

        ec.finishTreatment(); // UNDER_TREATMENT -> TREATMENT_FINISHED
        cases.save(ec);

        Visit visit = visits.findById(ec.getVisitId())
                .orElseThrow(() -> new NotFoundException("Visit not found: " + ec.getVisitId()));
        visit.transitionTo(VisitStatus.TREATMENT_FINISHED);
        visit.pullDomainEvents().forEach(events::publishEvent);

        ServiceItem dischargeFee = catalogue
                .findByCategoryAndCode(ServiceCategory.EMERGENCY, DISCHARGE_ITEM_CODE)
                .orElseThrow(() -> new DomainException("DISCHARGE_FEE_MISSING",
                        "Catalogue item " + DISCHARGE_ITEM_CODE + " is not configured"));
        Payment payment = createPayment.handle(new CreatePaymentCommand(
                visit.getId(), PaymentStage.FINAL,
                List.of(new CreatePaymentCommand.Line(dischargeFee.getId(), 1)), null));

        ec.scheduleDischargePayment(payment.getId()); // -> AWAITING_DISCHARGE_PAYMENT
        cases.save(ec);

        visit.transitionTo(VisitStatus.AWAITING_FINAL_PAYMENT);
        visit.pullDomainEvents().forEach(events::publishEvent);

        return ec;
    }
}
