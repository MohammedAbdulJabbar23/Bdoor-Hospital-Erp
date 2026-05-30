package com.albudoor.hms.premature.upsertform;

import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.domain.PrematureForm;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.premature.infrastructure.PrematureFormRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpsertFormHandler {

    private final PrematureFormRepository forms;
    private final PrematureAdmissionRepository admissions;

    public UpsertFormHandler(PrematureFormRepository forms, PrematureAdmissionRepository admissions) {
        this.forms = forms;
        this.admissions = admissions;
    }

    @Transactional
    public PrematureForm handle(UUID admissionId, UpsertFormCommand cmd) {
        PrematureAdmission adm = admissions.findById(admissionId)
                .orElseThrow(() -> new NotFoundException("Admission not found: " + admissionId));
        PrematureForm form = forms.findByAdmissionId(admissionId)
                .orElseGet(() -> PrematureForm.create(adm.getId(), adm.getVisitId(), adm.getPatientId()));
        form.update(cmd.toData());
        return forms.save(form);
    }
}
