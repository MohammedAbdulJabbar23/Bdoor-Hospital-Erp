package com.albudoor.hms.premature.listadmissions;

import com.albudoor.hms.premature.api.AdmissionResponse;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListAdmissionsHandler {

    private static final List<AdmissionStatus> ACTIVE = List.of(
            AdmissionStatus.AWAITING_ADMISSION_PAYMENT,
            AdmissionStatus.UNDER_CARE,
            AdmissionStatus.TREATMENT_FINISHED,
            AdmissionStatus.AWAITING_DISCHARGE_PAYMENT);

    private final PrematureAdmissionRepository admissions;

    public ListAdmissionsHandler(PrematureAdmissionRepository admissions) {
        this.admissions = admissions;
    }

    @Transactional(readOnly = true)
    public List<AdmissionResponse> list(AdmissionStatus status) {
        List<AdmissionStatus> filter = (status == null) ? ACTIVE : List.of(status);
        return admissions.findAllByStatusInOrderByAdmittedAtDesc(filter)
                .stream().map(AdmissionResponse::from).toList();
    }
}
