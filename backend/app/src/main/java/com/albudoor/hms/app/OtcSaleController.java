package com.albudoor.hms.app;

import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.catalogue.domain.ServiceItem;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.patientregistry.domain.Patient;
import com.albudoor.hms.patientregistry.infrastructure.PatientRepository;
import com.albudoor.hms.pharmacy.api.DispenseResponse;
import com.albudoor.hms.pharmacy.chargedispense.ChargeDispenseHandler;
import com.albudoor.hms.pharmacy.domain.DispenseLine;
import com.albudoor.hms.pharmacy.domain.PharmacyDispense;
import com.albudoor.hms.pharmacy.infrastructure.DispenseIdGenerator;
import com.albudoor.hms.pharmacy.infrastructure.PharmacyDispenseRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.createvisit.CreateVisitCommand;
import com.albudoor.hms.visitmanagement.createvisit.CreateVisitHandler;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Walk-in over-the-counter pharmacy sale. Patient walks up to the counter and buys drugs
 * without a prescription. A throwaway PHARMACY visit is created so the payment threads
 * through the central cashier exactly like any other visit-bound charge.
 */
@RestController
@RequestMapping("/api/pharmacy/walk-in-sales")
public class OtcSaleController {

    public record OtcLine(
            @NotNull UUID drugServiceItemId,
            @Positive int quantity
    ) {}

    public record OtcSaleBody(
            @NotNull UUID patientId,
            @NotEmpty List<OtcLine> lines
    ) {}

    public record OtcSaleResponse(
            DispenseResponse dispense,
            UUID visitId,
            String visitDisplayId
    ) {}

    private final PatientRepository patients;
    private final ServiceItemRepository catalogue;
    private final CreateVisitHandler createVisit;
    private final VisitRepository visits;
    private final PharmacyDispenseRepository dispenses;
    private final DispenseIdGenerator dispenseIds;
    private final ChargeDispenseHandler chargeDispense;
    private final ApplicationEventPublisher events;

    public OtcSaleController(
            PatientRepository patients,
            ServiceItemRepository catalogue,
            CreateVisitHandler createVisit,
            VisitRepository visits,
            PharmacyDispenseRepository dispenses,
            DispenseIdGenerator dispenseIds,
            ChargeDispenseHandler chargeDispense,
            ApplicationEventPublisher events
    ) {
        this.patients = patients;
        this.catalogue = catalogue;
        this.createVisit = createVisit;
        this.visits = visits;
        this.dispenses = dispenses;
        this.dispenseIds = dispenseIds;
        this.chargeDispense = chargeDispense;
        this.events = events;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    @Transactional
    public OtcSaleResponse sell(@Valid @RequestBody OtcSaleBody body) {
        Patient patient = patients.findById(body.patientId())
                .orElseThrow(() -> new NotFoundException("Patient not found: " + body.patientId()));

        // 1. Create a PHARMACY visit to anchor the cashier payment.
        Visit visit = createVisit.handle(new CreateVisitCommand(
                patient.getId(), VisitType.PHARMACY, null));

        // 2. Resolve catalogue items and build dispense lines (priced from catalogue).
        List<DispenseLine> dispenseLines = new ArrayList<>();
        for (OtcLine in : body.lines()) {
            ServiceItem item = catalogue.findById(in.drugServiceItemId())
                    .orElseThrow(() -> new NotFoundException(
                            "Drug not found: " + in.drugServiceItemId()));
            if (item.getCategory() != ServiceCategory.DRUG) {
                throw new DomainException("NOT_A_DRUG",
                        item.getCode() + " is not a drug catalogue item");
            }
            dispenseLines.add(DispenseLine.billable(
                    item.getId(), item.getCode(), item.getNameEn(),
                    item.getDrugDetails() == null ? null : item.getDrugDetails().getStrength(),
                    null, null, null, null, "OTC walk-in sale",
                    item.getFee(), in.quantity()));
        }

        // 3. Build dispense (PENDING) and charge it through the cashier.
        PharmacyDispense dispense = PharmacyDispense.otcSale(
                dispenseIds.next(),
                visit.getId(), visit.getVisitDisplayId(),
                patient.getId(), patient.getMrn(), patient.getFullName(),
                dispenseLines);
        dispenses.save(dispense);
        dispense.pullDomainEvents().forEach(events::publishEvent);

        PharmacyDispense charged = chargeDispense.handle(dispense.getId());

        // Walk the PHARMACY anchor visit from CREATED to AWAITING_PAYMENT so it's not
        // orphaned in the visit queue. Subsequent transitions are handled by
        // PaymentVisitBridge (→ IN_PROGRESS on cashier approve) and OtcVisitCompletionBridge
        // (→ COMPLETED on mark-given).
        Visit anchor = visits.findById(visit.getId()).orElseThrow();
        if (anchor.getStatus() == VisitStatus.CREATED) {
            anchor.transitionTo(VisitStatus.AWAITING_PAYMENT);
            anchor.pullDomainEvents().forEach(events::publishEvent);
        }

        return new OtcSaleResponse(
                DispenseResponse.from(charged),
                visit.getId(), visit.getVisitDisplayId());
    }
}
