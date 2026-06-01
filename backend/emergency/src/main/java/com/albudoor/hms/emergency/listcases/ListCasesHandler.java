package com.albudoor.hms.emergency.listcases;

import com.albudoor.hms.emergency.api.CaseResponse;
import com.albudoor.hms.emergency.domain.EmergencyCaseStatus;
import com.albudoor.hms.emergency.infrastructure.EmergencyCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
}
