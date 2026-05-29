package com.albudoor.hms.premature.admitpatient;

import com.albudoor.hms.cashier.createpayment.CreatePaymentCommand;
import com.albudoor.hms.cashier.createpayment.CreatePaymentHandler;
import com.albudoor.hms.cashier.domain.Payment;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.catalogue.domain.ServiceItem;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdmitPatientHandler {

    static final String ADMISSION_ITEM_CODE = "PREM-ADM";

    private final PrematureAdmissionRepository admissions;
    private final BedRepository beds;
    private final VisitRepository visits;
    private final ServiceItemRepository catalogue;
    private final CreatePaymentHandler createPayment;
    private final ApplicationEventPublisher events;

    public AdmitPatientHandler(PrematureAdmissionRepository admissions, BedRepository beds,
                               VisitRepository visits, ServiceItemRepository catalogue,
                               CreatePaymentHandler createPayment, ApplicationEventPublisher events) {
        this.admissions = admissions;
        this.beds = beds;
        this.visits = visits;
        this.catalogue = catalogue;
        this.createPayment = createPayment;
        this.events = events;
    }

    @Transactional
    public PrematureAdmission handle(AdmitPatientCommand cmd) {
        Visit visit = visits.findById(cmd.visitId())
                .orElseThrow(() -> new NotFoundException("Visit not found: " + cmd.visitId()));
        if (visit.getVisitType() != VisitType.PREMATURE) {
            throw new DomainException("NOT_PREMATURE_VISIT",
                    "Visit " + visit.getVisitDisplayId() + " is not a PREMATURE visit");
        }
        if (visit.getStatus() != VisitStatus.CREATED) {
            throw new DomainException("VISIT_NOT_ADMITTABLE",
                    "Visit must be CREATED to admit (status=" + visit.getStatus() + ")");
        }
        if (admissions.findByVisitId(visit.getId()).isPresent()) {
            throw new DomainException("ALREADY_ADMITTED", "Visit already has a premature admission");
        }

        Bed bed = beds.findById(cmd.bedId())
                .orElseThrow(() -> new NotFoundException("Bed not found: " + cmd.bedId()));
        bed.reserve();
        beds.save(bed);

        PrematureAdmission admission = PrematureAdmission.open(
                visit.getId(), visit.getVisitDisplayId(),
                visit.getPatientId(), visit.getPatientMrn(), visit.getPatientName(),
                bed.getId(), bed.getCode(),
                cmd.stayValue(), cmd.stayUnit());
        admissions.save(admission);

        ServiceItem admissionFee = catalogue
                .findByCategoryAndCode(ServiceCategory.PREMATURE, ADMISSION_ITEM_CODE)
                .orElseThrow(() -> new DomainException("ADMISSION_FEE_MISSING",
                        "Catalogue item " + ADMISSION_ITEM_CODE + " is not configured"));

        Payment payment = createPayment.handle(new CreatePaymentCommand(
                visit.getId(), PaymentStage.INITIAL,
                List.of(new CreatePaymentCommand.Line(admissionFee.getId(), 1)), null));
        admission.linkInitialPayment(payment.getId());
        admissions.save(admission);

        visit.transitionTo(VisitStatus.AWAITING_PAYMENT);
        visit.pullDomainEvents().forEach(events::publishEvent);

        return admission;
    }
}
