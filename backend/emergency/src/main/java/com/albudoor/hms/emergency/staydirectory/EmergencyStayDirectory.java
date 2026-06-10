package com.albudoor.hms.emergency.staydirectory;

import com.albudoor.hms.bedstayforms.directory.AgeText;
import com.albudoor.hms.bedstayforms.directory.StayDirectory;
import com.albudoor.hms.bedstayforms.directory.StayInfo;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.emergency.domain.EmergencyCaseStatus;
import com.albudoor.hms.emergency.infrastructure.EmergencyCaseRepository;
import com.albudoor.hms.patientregistry.infrastructure.PatientRepository;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Component
public class EmergencyStayDirectory implements StayDirectory {

    private final EmergencyCaseRepository cases;
    private final PatientRepository patients;

    public EmergencyStayDirectory(EmergencyCaseRepository cases, PatientRepository patients) {
        this.cases = cases;
        this.patients = patients;
    }

    @Override
    public StayDepartment department() { return StayDepartment.EMERGENCY; }

    @Override
    public Optional<StayInfo> find(UUID stayId) {
        return cases.findById(stayId).map(c -> {
            String age = patients.findById(c.getPatientId())
                    .map(p -> AgeText.derive(p.getDateOfBirth(),
                            c.getAdmittedAt().atZone(ZoneOffset.UTC).toLocalDate()))
                    .orElse(null);
            boolean open = c.getStatus() != EmergencyCaseStatus.CLOSED
                    && c.getStatus() != EmergencyCaseStatus.CANCELLED;
            return new StayInfo(c.getPatientId(), c.getPatientName(), c.getPatientMrn(),
                    age, c.getAdmittedAt(), open);
        });
    }
}
