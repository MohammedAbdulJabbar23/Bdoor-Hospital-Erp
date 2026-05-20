package com.albudoor.hms.cashier.createpayment;

import com.albudoor.hms.cashier.domain.Payment;
import com.albudoor.hms.cashier.domain.PaymentLineItem;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.cashier.infrastructure.PaymentIdGenerator;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.catalogue.domain.ServiceItem;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.patientregistry.domain.Patient;
import com.albudoor.hms.patientregistry.infrastructure.PatientRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CreatePaymentHandler {

    private static final String DEFAULT_CURRENCY = "IQD";

    private final PaymentRepository payments;
    private final VisitRepository visits;
    private final PatientRepository patients;
    private final ServiceItemRepository services;
    private final PaymentIdGenerator idGenerator;
    private final ApplicationEventPublisher events;

    public CreatePaymentHandler(
            PaymentRepository payments,
            VisitRepository visits,
            PatientRepository patients,
            ServiceItemRepository services,
            PaymentIdGenerator idGenerator,
            ApplicationEventPublisher events
    ) {
        this.payments = payments;
        this.visits = visits;
        this.patients = patients;
        this.services = services;
        this.idGenerator = idGenerator;
        this.events = events;
    }

    @Transactional
    public Payment handle(CreatePaymentCommand cmd) {
        Visit visit = visits.findById(cmd.visitId())
                .orElseThrow(() -> new NotFoundException("Visit not found: " + cmd.visitId()));
        Patient patient = patients.findById(visit.getPatientId())
                .orElseThrow(() -> new NotFoundException(
                        "Patient not found: " + visit.getPatientId()));

        String currency = (cmd.currency() == null || cmd.currency().isBlank())
                ? DEFAULT_CURRENCY
                : cmd.currency();

        List<PaymentLineItem> lines = new ArrayList<>(cmd.lines().size());
        for (CreatePaymentCommand.Line input : cmd.lines()) {
            ServiceItem item = services.findById(input.serviceItemId())
                    .orElseThrow(() -> new NotFoundException(
                            "Service item not found: " + input.serviceItemId()));
            if (!item.isActive()) {
                throw new DomainException("SERVICE_ARCHIVED",
                        "Service item is archived: " + item.getCode());
            }
            if (item.getForwardTo() != null) {
                throw new DomainException("SERVICE_FORWARDS",
                        "Service item " + item.getCode() + " is a forwarding pointer; "
                                + "create the payment in the receiving department instead.");
            }
            if (item.getFee() == null) {
                throw new DomainException("SERVICE_FEE_MISSING",
                        "Service item " + item.getCode() + " has no fee configured");
            }
            if (!item.getCurrency().equalsIgnoreCase(currency)) {
                throw new DomainException("CURRENCY_MISMATCH",
                        "Service item " + item.getCode() + " is in " + item.getCurrency()
                                + " but payment is in " + currency);
            }
            lines.add(PaymentLineItem.of(
                    item.getId(),
                    item.getCode(),
                    item.getNameEn(),
                    item.getFee(),
                    input.quantity()
            ));
        }

        Payment payment = Payment.create(
                idGenerator.next(),
                visit.getId(), visit.getVisitDisplayId(), visit.getVisitType(),
                patient.getId(), patient.getMrn(), patient.getFullName(), patient.isVip(),
                cmd.stage(),
                currency,
                lines
        );
        Payment saved = payments.save(payment);
        // Spring Data JPA calls EntityManager.merge() for entities with pre-assigned ids,
        // returning a different managed instance — domain events are still on the source
        // reference, so pull from `payment`, not `saved`.
        payment.pullDomainEvents().forEach(events::publishEvent);
        return saved;
    }

    /**
     * Create a consult-fee payment for a visit with an ad-hoc line priced from the doctor's
     * {@code consultationFee}. Stage is always {@code INITIAL} (the doctor visit is the first
     * payable event of a doctor-appointment visit).
     */
    @Transactional
    public Payment handleConsult(java.util.UUID visitId, String doctorName, BigDecimal fee, String currencyOverride) {
        if (fee == null || fee.signum() <= 0) {
            throw new DomainException("CONSULT_FEE_MISSING",
                    "Doctor has no consultation fee configured: " + doctorName);
        }
        Visit visit = visits.findById(visitId)
                .orElseThrow(() -> new NotFoundException("Visit not found: " + visitId));
        Patient patient = patients.findById(visit.getPatientId())
                .orElseThrow(() -> new NotFoundException(
                        "Patient not found: " + visit.getPatientId()));

        String currency = (currencyOverride == null || currencyOverride.isBlank())
                ? DEFAULT_CURRENCY : currencyOverride;

        PaymentLineItem line = PaymentLineItem.adHoc(
                "CONSULT", "Consultation – " + doctorName, fee, 1);

        Payment payment = Payment.create(
                idGenerator.next(),
                visit.getId(), visit.getVisitDisplayId(), visit.getVisitType(),
                patient.getId(), patient.getMrn(), patient.getFullName(), patient.isVip(),
                PaymentStage.INITIAL,
                currency,
                List.of(line)
        );
        Payment saved = payments.save(payment);
        payment.pullDomainEvents().forEach(events::publishEvent);
        return saved;
    }
}
