package com.albudoor.hms.visitmanagement.createvisit;

import com.albudoor.hms.patientregistry.domain.Patient;
import com.albudoor.hms.patientregistry.infrastructure.PatientRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitOrigin;
import com.albudoor.hms.visitmanagement.infrastructure.VisitIdGenerator;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateVisitHandler {

    private final VisitRepository visits;
    private final PatientRepository patients;
    private final VisitIdGenerator idGenerator;
    private final ApplicationEventPublisher events;

    public CreateVisitHandler(
            VisitRepository visits,
            PatientRepository patients,
            VisitIdGenerator idGenerator,
            ApplicationEventPublisher events
    ) {
        this.visits = visits;
        this.patients = patients;
        this.idGenerator = idGenerator;
        this.events = events;
    }

    @Transactional
    public Visit handle(CreateVisitCommand cmd) {
        Patient patient = patients.findById(cmd.patientId())
                .orElseThrow(() -> new NotFoundException("Patient not found: " + cmd.patientId()));

        Visit visit = Visit.createDirect(
                idGenerator.next(),
                patient.getId(),
                patient.getMrn(),
                patient.getFullName(),
                cmd.visitType(),
                cmd.origin() == null ? VisitOrigin.DIRECT_RETURNING : cmd.origin(),
                cmd.assignedDoctorId()
        );
        Visit saved = visits.save(visit);
        // See note in CreatePaymentHandler — pull events from the source reference,
        // not from `saved`, since Spring Data merges entities with pre-assigned ids.
        visit.pullDomainEvents().forEach(events::publishEvent);
        return saved;
    }
}
