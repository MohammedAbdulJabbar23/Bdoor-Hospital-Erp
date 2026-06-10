package com.albudoor.hms.premature.getcase;

import com.albudoor.hms.patientregistry.domain.InfantDetails;
import com.albudoor.hms.patientregistry.domain.Patient;
import com.albudoor.hms.patientregistry.infrastructure.PatientRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.api.AdmissionResponse;
import com.albudoor.hms.premature.api.PatientCaseFormResponse;
import com.albudoor.hms.premature.api.PrematureCaseResponse;
import com.albudoor.hms.premature.api.PrematureFormResponse;
import com.albudoor.hms.premature.api.PrematureTourResponse;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.PatientCaseFormRepository;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.premature.infrastructure.PrematureFormRepository;
import com.albudoor.hms.premature.infrastructure.PrematureTourRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class GetCaseHandler {

    private final PrematureAdmissionRepository admissions;
    private final PrematureFormRepository forms;
    private final PrematureTourRepository tours;
    private final PatientRepository patients;
    private final PatientCaseFormRepository caseForms;

    public GetCaseHandler(PrematureAdmissionRepository admissions, PrematureFormRepository forms,
                          PrematureTourRepository tours, PatientRepository patients,
                          PatientCaseFormRepository caseForms) {
        this.admissions = admissions;
        this.forms = forms;
        this.tours = tours;
        this.patients = patients;
        this.caseForms = caseForms;
    }

    @Transactional(readOnly = true)
    public PrematureCaseResponse handle(UUID admissionId) {
        PrematureAdmission adm = admissions.findById(admissionId)
                .orElseThrow(() -> new NotFoundException("Admission not found: " + admissionId));
        PrematureFormResponse form = forms.findByAdmissionId(admissionId)
                .map(PrematureFormResponse::from).orElse(null);
        var tourList = tours.findAllByAdmissionIdOrderByRecordedAtDesc(admissionId)
                .stream().map(PrematureTourResponse::from).toList();
        PatientCaseFormResponse caseForm = caseForms.findByAdmissionId(admissionId)
                .map(PatientCaseFormResponse::from).orElse(null);
        Patient patient = patients.findById(adm.getPatientId()).orElse(null);
        InfantDetails details = patient == null ? null : patient.getInfantDetails();
        var caseFilePrefill = new PrematureCaseResponse.CaseFilePrefill(
                details == null ? null : details.getMotherName(),
                patient == null || patient.getGender() == null ? null : patient.getGender().name());
        return new PrematureCaseResponse(AdmissionResponse.from(adm), form, prefill(adm, patient), tourList,
                caseForm, caseFilePrefill);
    }

    private PrematureCaseResponse.Prefill prefill(PrematureAdmission adm, Patient p) {
        InfantDetails d = (p == null) ? null : p.getInfantDetails();
        String age = (p == null) ? null : deriveAge(p.getDateOfBirth(),
                adm.getAdmittedAt().atZone(ZoneOffset.UTC).toLocalDate());
        if (d == null) return new PrematureCaseResponse.Prefill(age, null, null, null, null, null);
        return new PrematureCaseResponse.Prefill(age,
                d.getBirthWeightKg(), d.getGestationalAgeWeeks(), d.getGestationalAgeDays(),
                d.getLengthCm(), d.getOfcCm());
    }

    /** Human-readable age at admission, e.g. "12 days" / "3 weeks". Package-visible for testing. */
    static String deriveAge(LocalDate dob, LocalDate at) {
        if (dob == null || at == null || at.isBefore(dob)) return null;
        long days = ChronoUnit.DAYS.between(dob, at);
        if (days < 14) return days + (days == 1 ? " day" : " days");
        return (days / 7) + " weeks";
    }
}
