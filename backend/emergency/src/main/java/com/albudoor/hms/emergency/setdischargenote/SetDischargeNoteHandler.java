package com.albudoor.hms.emergency.setdischargenote;

import com.albudoor.hms.emergency.domain.EmergencyCase;
import com.albudoor.hms.emergency.infrastructure.EmergencyCaseRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service("emergencySetDischargeNoteHandler")
public class SetDischargeNoteHandler {
    private final EmergencyCaseRepository cases;
    public SetDischargeNoteHandler(EmergencyCaseRepository cases) { this.cases = cases; }

    @Transactional
    public EmergencyCase handle(UUID caseId, SetDischargeNoteCommand cmd) {
        EmergencyCase c = cases.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Case not found: " + caseId));
        c.setDischargeNote(cmd.note());
        return cases.save(c);
    }
}
