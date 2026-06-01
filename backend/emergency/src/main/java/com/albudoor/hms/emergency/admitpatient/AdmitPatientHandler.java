package com.albudoor.hms.emergency.admitpatient;

import com.albudoor.hms.cashier.createpayment.CreatePaymentCommand;
import com.albudoor.hms.cashier.createpayment.CreatePaymentHandler;
import com.albudoor.hms.cashier.domain.Payment;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.catalogue.domain.ServiceItem;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.emergency.domain.EmergencyBed;
import com.albudoor.hms.emergency.domain.EmergencyCase;
import com.albudoor.hms.emergency.domain.EmergencyServiceCodes;
import com.albudoor.hms.emergency.infrastructure.EmergencyBedRepository;
import com.albudoor.hms.emergency.infrastructure.EmergencyCaseRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service("emergencyAdmitPatientHandler")
public class AdmitPatientHandler {

    private final EmergencyCaseRepository cases;
    private final EmergencyBedRepository beds;
    private final VisitRepository visits;
    private final ServiceItemRepository catalogue;
    private final CreatePaymentHandler createPayment;
    private final ApplicationEventPublisher events;

    public AdmitPatientHandler(EmergencyCaseRepository cases, EmergencyBedRepository beds,
                               VisitRepository visits, ServiceItemRepository catalogue,
                               CreatePaymentHandler createPayment, ApplicationEventPublisher events) {
        this.cases = cases; this.beds = beds; this.visits = visits;
        this.catalogue = catalogue; this.createPayment = createPayment; this.events = events;
    }

    @Transactional
    public EmergencyCase handle(AdmitPatientCommand cmd) {
        Visit visit = visits.findById(cmd.visitId())
                .orElseThrow(() -> new NotFoundException("Visit not found: " + cmd.visitId()));
        if (visit.getVisitType() != VisitType.EMERGENCY) {
            throw new DomainException("NOT_EMERGENCY_VISIT", "Visit is not an EMERGENCY visit");
        }
        if (visit.getStatus() != VisitStatus.CREATED) {
            throw new DomainException("VISIT_NOT_ADMITTABLE", "Visit must be CREATED to admit (status=" + visit.getStatus() + ")");
        }
        if (cases.findByVisitId(visit.getId()).isPresent()) {
            throw new DomainException("ALREADY_ADMITTED", "Visit already has an emergency case");
        }
        ServiceItem service = catalogue.findById(cmd.serviceItemId())
                .orElseThrow(() -> new NotFoundException("Service not found: " + cmd.serviceItemId()));
        if (service.getCategory() != ServiceCategory.EMERGENCY || !service.isActive() || service.getForwardTo() != null
                || EmergencyServiceCodes.DISCHARGE.equals(service.getCode())) {
            throw new DomainException("INVALID_EMERGENCY_SERVICE", "Not a billable emergency service: " + cmd.serviceItemId());
        }

        EmergencyBed bed = beds.findById(cmd.bedId())
                .orElseThrow(() -> new NotFoundException("Bed not found: " + cmd.bedId()));
        bed.reserve();
        beds.save(bed);

        EmergencyCase ec = EmergencyCase.open(
                visit.getId(), visit.getVisitDisplayId(),
                visit.getPatientId(), visit.getPatientMrn(), visit.getPatientName(),
                bed.getId(), bed.getCode(),
                service.getId(), service.getCode(), service.getNameEn(),
                cmd.stayValue(), cmd.stayUnit());
        cases.save(ec);

        Payment payment = createPayment.handle(new CreatePaymentCommand(
                visit.getId(), PaymentStage.INITIAL,
                List.of(new CreatePaymentCommand.Line(service.getId(), 1)), null));
        ec.linkInitialPayment(payment.getId());
        cases.save(ec);

        visit.transitionTo(VisitStatus.AWAITING_PAYMENT);
        visit.pullDomainEvents().forEach(events::publishEvent);
        return ec;
    }
}
