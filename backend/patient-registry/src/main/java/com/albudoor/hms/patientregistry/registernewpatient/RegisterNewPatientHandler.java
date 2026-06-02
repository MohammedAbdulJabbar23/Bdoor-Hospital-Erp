package com.albudoor.hms.patientregistry.registernewpatient;

import com.albudoor.hms.patientregistry.domain.AdultDetails;
import com.albudoor.hms.patientregistry.domain.Patient;
import com.albudoor.hms.patientregistry.infrastructure.MrnGenerator;
import com.albudoor.hms.patientregistry.infrastructure.PatientRepository;
import com.albudoor.hms.platform.exception.ConflictException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterNewPatientHandler {

    private final PatientRepository patients;
    private final MrnGenerator mrnGenerator;
    private final ApplicationEventPublisher eventPublisher;

    public RegisterNewPatientHandler(
            PatientRepository patients,
            MrnGenerator mrnGenerator,
            ApplicationEventPublisher eventPublisher
    ) {
        this.patients = patients;
        this.mrnGenerator = mrnGenerator;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Patient handle(RegisterNewPatientCommand cmd) {
        // National-ID uniqueness is enforced both here (fast, friendly 409) and at the DB
        // level via a UNIQUE partial index on national_id WHERE national_id IS NOT NULL
        // (migration V023) which catches concurrent inserts that race past this check.
        String nationalId = blankToNull(cmd.nationalId());
        if (nationalId != null && patients.existsByAdultDetails_NationalId(nationalId)) {
            throw new ConflictException("DUPLICATE_PATIENT",
                    "A patient with national ID " + nationalId + " already exists");
        }

        String mrn = mrnGenerator.next();
        if (patients.existsByMrn(mrn)) {
            throw new ConflictException("MRN_COLLISION", "MRN collision; retry registration");
        }

        AdultDetails details = new AdultDetails(
                nationalId,
                blankToNull(cmd.mobileNumber()),
                blankToNull(cmd.address()),
                blankToNull(cmd.occupation()),
                blankToNull(cmd.emergencyContactName()),
                blankToNull(cmd.emergencyContactMobile())
        );

        Patient patient = Patient.registerAdult(
                mrn, cmd.fullName(), cmd.gender(), cmd.dateOfBirth(), details, cmd.vip()
        );

        Patient saved = patients.save(patient);
        // Pull events from the source reference; merge() on pre-assigned ids returns a
        // different managed instance with no carried-over domain events.
        patient.pullDomainEvents().forEach(eventPublisher::publishEvent);
        return saved;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
