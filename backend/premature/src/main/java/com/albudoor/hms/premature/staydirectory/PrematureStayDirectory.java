package com.albudoor.hms.premature.staydirectory;

import com.albudoor.hms.bedstayforms.directory.AgeText;
import com.albudoor.hms.bedstayforms.directory.StayDirectory;
import com.albudoor.hms.bedstayforms.directory.StayInfo;
import com.albudoor.hms.bedstayforms.directory.StayOrderRef;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.patientregistry.infrastructure.PatientRepository;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PrematureStayDirectory implements StayDirectory {

    private final PrematureAdmissionRepository admissions;
    private final PatientRepository patients;
    private final VisitRepository visits;

    public PrematureStayDirectory(PrematureAdmissionRepository admissions, PatientRepository patients,
                                  VisitRepository visits) {
        this.admissions = admissions;
        this.patients = patients;
        this.visits = visits;
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

    @Override
    public List<StayOrderRef> orders(UUID stayId) {
        return admissions.findById(stayId)
                .map(a -> visits.findAllByParentVisitIdOrderByStartedAtDesc(a.getVisitId()).stream()
                        .map(v -> new StayOrderRef(v.getId(), v.getVisitType().name(), v.getStartedAt()))
                        .toList())
                .orElse(List.of());
    }
}
