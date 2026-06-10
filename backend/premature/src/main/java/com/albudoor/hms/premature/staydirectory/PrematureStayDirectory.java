package com.albudoor.hms.premature.staydirectory;

import com.albudoor.hms.bedstayforms.directory.AgeText;
import com.albudoor.hms.bedstayforms.directory.StayDirectory;
import com.albudoor.hms.bedstayforms.directory.StayInfo;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.patientregistry.infrastructure.PatientRepository;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Component
public class PrematureStayDirectory implements StayDirectory {

    private final PrematureAdmissionRepository admissions;
    private final PatientRepository patients;

    public PrematureStayDirectory(PrematureAdmissionRepository admissions, PatientRepository patients) {
        this.admissions = admissions;
        this.patients = patients;
    }

    @Override
    public StayDepartment department() { return StayDepartment.PREMATURE; }

    @Override
    public Optional<StayInfo> find(UUID stayId) {
        return admissions.findById(stayId).map(a -> {
            String age = patients.findById(a.getPatientId())
                    .map(p -> AgeText.derive(p.getDateOfBirth(),
                            a.getAdmittedAt().atZone(ZoneOffset.UTC).toLocalDate()))
                    .orElse(null);
            boolean open = a.getStatus() != AdmissionStatus.CLOSED
                    && a.getStatus() != AdmissionStatus.CANCELLED;
            return new StayInfo(a.getPatientId(), a.getPatientName(), a.getPatientMrn(),
                    age, a.getAdmittedAt(), open);
        });
    }
}
