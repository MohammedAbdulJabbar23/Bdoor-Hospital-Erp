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
        // National-ID uniqueness is a soft constraint — handled at the DB unique index level
        // when the client confirms the rule. For now, MRN is the only hard unique key.
        String mrn = mrnGenerator.next();
        if (patients.existsByMrn(mrn)) {
            throw new ConflictException("MRN_COLLISION", "MRN collision; retry registration");
        }

        AdultDetails details = new AdultDetails(
                blankToNull(cmd.nationalId()),
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
        saved.pullDomainEvents().forEach(eventPublisher::publishEvent);
        return saved;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
