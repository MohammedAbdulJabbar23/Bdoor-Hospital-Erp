package com.albudoor.hms.emergency.listcases;

import com.albudoor.hms.emergency.api.CaseResponse;
import com.albudoor.hms.emergency.domain.EmergencyCaseStatus;
import com.albudoor.hms.emergency.infrastructure.EmergencyCaseRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service("emergencyListCasesHandler")
public class ListCasesHandler {

    private static final List<EmergencyCaseStatus> ACTIVE = List.of(
            EmergencyCaseStatus.AWAITING_INITIAL_PAYMENT,
            EmergencyCaseStatus.UNDER_TREATMENT,
            EmergencyCaseStatus.TREATMENT_FINISHED,
            EmergencyCaseStatus.AWAITING_DISCHARGE_PAYMENT);

    private final EmergencyCaseRepository cases;

    public ListCasesHandler(EmergencyCaseRepository cases) {
        this.cases = cases;
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> list(EmergencyCaseStatus status) {
        List<EmergencyCaseStatus> filter = (status == null) ? ACTIVE : List.of(status);
        return cases.findAllByStatusInOrderByAdmittedAtDesc(filter)
                .stream().map(CaseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CaseResponse byId(UUID id) {
        return cases.findById(id).map(CaseResponse::from)
                .orElseThrow(() -> new NotFoundException("Emergency case not found: " + id));
    }
}
