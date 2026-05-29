package com.albudoor.hms.premature.extendstay;

import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ExtendStayHandler {

    private final PrematureAdmissionRepository admissions;

    public ExtendStayHandler(PrematureAdmissionRepository admissions) {
        this.admissions = admissions;
    }

    @Transactional
    public PrematureAdmission handle(UUID admissionId, ExtendStayCommand cmd) {
        PrematureAdmission a = admissions.findById(admissionId)
                .orElseThrow(() -> new NotFoundException("Admission not found: " + admissionId));
        a.extendStay(cmd.value(), cmd.unit());
        return admissions.save(a);
    }
}
