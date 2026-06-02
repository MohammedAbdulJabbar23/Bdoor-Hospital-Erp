package com.albudoor.hms.premature.finishtreatment;

import com.albudoor.hms.cashier.createpayment.CreatePaymentCommand;
import com.albudoor.hms.cashier.createpayment.CreatePaymentHandler;
import com.albudoor.hms.cashier.domain.Payment;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.catalogue.domain.ServiceItem;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.platform.exception.ResultsPendingException;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FinishTreatmentHandler {

    static final String DISCHARGE_ITEM_CODE = "PREM-DIS";

    private final PrematureAdmissionRepository admissions;
    private final VisitRepository visits;
    private final ServiceItemRepository catalogue;
    private final CreatePaymentHandler createPayment;
    private final ApplicationEventPublisher events;

    public FinishTreatmentHandler(PrematureAdmissionRepository admissions, VisitRepository visits,
                                  ServiceItemRepository catalogue, CreatePaymentHandler createPayment,
                                  ApplicationEventPublisher events) {
        this.admissions = admissions;
        this.visits = visits;
        this.catalogue = catalogue;
        this.createPayment = createPayment;
        this.events = events;
    }

    @Transactional
    public PrematureAdmission handle(UUID admissionId, FinishTreatmentCommand cmd) {
        PrematureAdmission admission = admissions.findById(admissionId)
                .orElseThrow(() -> new NotFoundException("Admission not found: " + admissionId));

        // Results-pending gate (warn + override): open = child visit not COMPLETED/CANCELLED.
        List<Visit> children = visits.findAllByParentVisitIdOrderByStartedAtDesc(admission.getVisitId());
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
            admission.recordFinishOverride(cmd.overrideReason()); // throws if reason blank
        }

        admission.finishTreatment(); // UNDER_CARE -> TREATMENT_FINISHED
        admissions.save(admission);

        Visit visit = visits.findById(admission.getVisitId())
                .orElseThrow(() -> new NotFoundException("Visit not found: " + admission.getVisitId()));
        visit.transitionTo(VisitStatus.TREATMENT_FINISHED);
        visit.pullDomainEvents().forEach(events::publishEvent);

        ServiceItem dischargeFee = catalogue
                .findByCategoryAndCode(ServiceCategory.PREMATURE, DISCHARGE_ITEM_CODE)
                .orElseThrow(() -> new DomainException("DISCHARGE_FEE_MISSING",
                        "Catalogue item " + DISCHARGE_ITEM_CODE + " is not configured"));
        Payment payment = createPayment.handle(new CreatePaymentCommand(
                visit.getId(), PaymentStage.FINAL,
                List.of(new CreatePaymentCommand.Line(dischargeFee.getId(), 1)), null));

        admission.scheduleDischargePayment(payment.getId()); // -> AWAITING_DISCHARGE_PAYMENT
        admissions.save(admission);

        visit.transitionTo(VisitStatus.AWAITING_FINAL_PAYMENT);
        visit.pullDomainEvents().forEach(events::publishEvent);

        return admission;
    }
}
