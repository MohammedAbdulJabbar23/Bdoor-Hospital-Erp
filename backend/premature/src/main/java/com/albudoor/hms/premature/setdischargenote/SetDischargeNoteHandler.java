package com.albudoor.hms.premature.setdischargenote;

import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class SetDischargeNoteHandler {
    private final PrematureAdmissionRepository admissions;
    public SetDischargeNoteHandler(PrematureAdmissionRepository admissions) { this.admissions = admissions; }

    @Transactional
    public PrematureAdmission handle(UUID admissionId, SetDischargeNoteCommand cmd) {
        PrematureAdmission a = admissions.findById(admissionId)
                .orElseThrow(() -> new NotFoundException("Admission not found: " + admissionId));
        a.setDischargeNote(cmd.note());
        return admissions.save(a);
    }
}
