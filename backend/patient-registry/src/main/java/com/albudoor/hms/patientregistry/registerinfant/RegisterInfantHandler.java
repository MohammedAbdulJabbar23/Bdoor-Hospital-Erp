package com.albudoor.hms.patientregistry.registerinfant;

import com.albudoor.hms.patientregistry.domain.InfantDetails;
import com.albudoor.hms.patientregistry.domain.Patient;
import com.albudoor.hms.patientregistry.infrastructure.MrnGenerator;
import com.albudoor.hms.patientregistry.infrastructure.PatientRepository;
import com.albudoor.hms.platform.exception.ConflictException;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterInfantHandler {

    private final PatientRepository patients;
    private final MrnGenerator mrnGenerator;
    private final ApplicationEventPublisher eventPublisher;

    public RegisterInfantHandler(
            PatientRepository patients,
            MrnGenerator mrnGenerator,
            ApplicationEventPublisher eventPublisher
    ) {
        this.patients = patients;
        this.mrnGenerator = mrnGenerator;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Patient handle(RegisterInfantCommand cmd) {
        if (cmd.motherPatientId() != null && !patients.existsById(cmd.motherPatientId())) {
            throw new NotFoundException("Mother patient not found: " + cmd.motherPatientId());
        }

        String mrn = mrnGenerator.next();
        if (patients.existsByMrn(mrn)) {
            throw new ConflictException("MRN_COLLISION", "MRN collision; retry registration");
        }

        String fullName = cmd.fullName();
        if (fullName == null || fullName.isBlank()) {
            fullName = deriveTemporaryName(cmd.motherName());
        }

        InfantDetails details = new InfantDetails(
                cmd.motherPatientId(),
                blankToNull(cmd.motherName()),
                blankToNull(cmd.motherNationalId()),
                blankToNull(cmd.motherMobile()),
                blankToNull(cmd.fatherName()),
                blankToNull(cmd.fatherMobile()),
                cmd.dobTime(),
                cmd.placeOfBirth(),
                cmd.deliveryType(),
                cmd.apgar1Min(),
                cmd.apgar5Min(),
                cmd.birthWeightKg(),
                cmd.lengthCm(),
                cmd.ofcCm(),
                cmd.gestationalAgeWeeks(),
                cmd.gestationalAgeDays(),
                cmd.guardianName(),
                blankToNull(cmd.guardianRelationship()),
                blankToNull(cmd.guardianMobile()),
                blankToNull(cmd.guardianNationalId())
        );

        Patient infant = Patient.registerInfant(
                mrn, fullName, cmd.gender(), cmd.dateOfBirth(), details, cmd.vip()
        );

        Patient saved = patients.save(infant);
        saved.pullDomainEvents().forEach(eventPublisher::publishEvent);
        return saved;
    }

    private static String deriveTemporaryName(String motherName) {
        if (motherName == null || motherName.isBlank()) {
            return "Baby (unnamed)";
        }
        String[] parts = motherName.trim().split("\\s+");
        String family = parts[parts.length - 1];
        return "Baby " + family;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
