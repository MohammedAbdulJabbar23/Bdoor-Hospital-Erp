package com.albudoor.hms.departmentservices.opencase;

import com.albudoor.hms.cashier.createpayment.CreatePaymentCommand;
import com.albudoor.hms.cashier.createpayment.CreatePaymentHandler;
import com.albudoor.hms.cashier.domain.Payment;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.catalogue.domain.ServiceItem;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.departmentservices.domain.CaseServiceLine;
import com.albudoor.hms.departmentservices.domain.DepartmentCase;
import com.albudoor.hms.departmentservices.domain.DepartmentCategory;
import com.albudoor.hms.departmentservices.infrastructure.DepartmentCaseRepository;
import com.albudoor.hms.departmentservices.security.DepartmentRoleGuard;
import com.albudoor.hms.platform.exception.ConflictException;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitOrigin;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenCaseHandler {

    private final DepartmentCaseRepository cases;
    private final VisitRepository visits;
    private final ServiceItemRepository catalogue;
    private final CreatePaymentHandler createPayment;
    private final ApplicationEventPublisher events;
    private final DepartmentRoleGuard roleGuard;

    public OpenCaseHandler(
            DepartmentCaseRepository cases,
            VisitRepository visits,
            ServiceItemRepository catalogue,
            CreatePaymentHandler createPayment,
            ApplicationEventPublisher events,
            DepartmentRoleGuard roleGuard
    ) {
        this.cases = cases;
        this.visits = visits;
        this.catalogue = catalogue;
        this.createPayment = createPayment;
        this.events = events;
        this.roleGuard = roleGuard;
    }

    @Transactional
    public DepartmentCase handle(OpenCaseCommand cmd) {
        // The caller's department role must match the case's category (ADMIN bypasses).
        roleGuard.requireDepartmentMatches(cmd.category());

        Visit visit = visits.findById(cmd.visitId())
                .orElseThrow(() -> new NotFoundException("Visit not found: " + cmd.visitId()));

        // Visit type must match the department category.
        if (visit.getVisitType() != cmd.category().visitType()) {
            throw new DomainException("VISIT_TYPE_MISMATCH",
                    "Visit type " + visit.getVisitType()
                            + " cannot be opened in " + cmd.category() + " department");
        }
        if (visit.getStatus() == VisitStatus.CANCELLED || visit.getStatus() == VisitStatus.COMPLETED) {
            throw new ConflictException("VISIT_TERMINAL",
                    "Visit is " + visit.getStatus() + "; cannot open a department case");
        }

        DepartmentCase deptCase = cases.findByVisitId(visit.getId())
                .orElseGet(() -> cases.save(DepartmentCase.open(
                        cmd.category(),
                        visit.getId(), visit.getVisitDisplayId(),
                        visit.getOrigin(), visit.getParentVisitId(),
                        visit.getPatientId(), visit.getPatientMrn(), visit.getPatientName(),
                        visit.getReferralNote())));

        // Append the requested service lines.
        for (OpenCaseCommand.Service in : cmd.services()) {
            ServiceItem item = catalogue.findById(in.serviceItemId())
                    .orElseThrow(() -> new NotFoundException(
                            "Service item not found: " + in.serviceItemId()));
            if (!item.isActive()) {
                throw new DomainException("SERVICE_ARCHIVED",
                        "Service item is archived: " + item.getCode());
            }
            if (item.getCategory() != cmd.category().catalogueCategory()) {
                throw new DomainException("SERVICE_WRONG_CATEGORY",
                        "Service item " + item.getCode() + " belongs to " + item.getCategory()
                                + ", not " + cmd.category().catalogueCategory());
            }
            // For phase 1 we treat the case-line as the unit; quantity > 1 still
            // bills correctly because the cashier receives a single line with that quantity.
            deptCase.addService(CaseServiceLine.pending(
                    item.getId(), item.getCode(), item.getNameEn(), item.getFee()));
        }

        // Create the payment via cashier; stage depends on visit origin.
        PaymentStage stage = (visit.getOrigin() == VisitOrigin.FORWARDED)
                ? PaymentStage.REFERRAL
                : PaymentStage.INITIAL;

        var lines = cmd.services().stream()
                .map(s -> new CreatePaymentCommand.Line(s.serviceItemId(), s.quantity()))
                .toList();
        Payment payment = createPayment.handle(new CreatePaymentCommand(
                visit.getId(), stage, lines, null));

        deptCase.linkPayment(payment.getId());

        // Move visit to AWAITING_PAYMENT (only if it's currently CREATED).
        if (visit.getStatus() == VisitStatus.CREATED) {
            visit.transitionTo(VisitStatus.AWAITING_PAYMENT);
            visit.pullDomainEvents().forEach(events::publishEvent);
        }

        return deptCase;
    }
}
