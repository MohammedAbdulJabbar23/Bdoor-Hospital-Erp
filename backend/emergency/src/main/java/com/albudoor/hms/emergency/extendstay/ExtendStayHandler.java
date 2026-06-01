package com.albudoor.hms.emergency.extendstay;

import com.albudoor.hms.emergency.domain.EmergencyCase;
import com.albudoor.hms.emergency.infrastructure.EmergencyCaseRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service("emergencyExtendStayHandler")
public class ExtendStayHandler {

    private final EmergencyCaseRepository cases;

    public ExtendStayHandler(EmergencyCaseRepository cases) {
        this.cases = cases;
    }

    @Transactional
    public EmergencyCase handle(UUID caseId, ExtendStayCommand cmd) {
        EmergencyCase ec = cases.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Case not found: " + caseId));
        ec.extendStay(cmd.value(), cmd.unit());
        return cases.save(ec);
    }
}
