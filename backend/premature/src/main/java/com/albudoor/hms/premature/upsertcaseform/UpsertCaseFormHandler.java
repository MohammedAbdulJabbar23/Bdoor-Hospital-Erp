package com.albudoor.hms.premature.upsertcaseform;

import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.domain.PatientCaseForm;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.PatientCaseFormRepository;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpsertCaseFormHandler {

    private final PatientCaseFormRepository forms;
    private final PrematureAdmissionRepository admissions;

    public UpsertCaseFormHandler(PatientCaseFormRepository forms, PrematureAdmissionRepository admissions) {
        this.forms = forms;
        this.admissions = admissions;
    }

    @Transactional
    public PatientCaseForm handle(UUID admissionId, UpsertCaseFormCommand cmd) {
        PrematureAdmission adm = admissions.findById(admissionId)
                .orElseThrow(() -> new NotFoundException("Admission not found: " + admissionId));
        if (adm.getStatus() == AdmissionStatus.CLOSED || adm.getStatus() == AdmissionStatus.CANCELLED) {
            throw new DomainException("STAY_CLOSED", "The case is closed; the case form is read-only");
        }
        PatientCaseForm form = forms.findByAdmissionId(admissionId)
                .orElseGet(() -> PatientCaseForm.create(adm.getId()));
        form.update(cmd.toData());
        return forms.save(form);
    }
}
