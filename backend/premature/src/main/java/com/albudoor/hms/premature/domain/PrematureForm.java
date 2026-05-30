package com.albudoor.hms.premature.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "prem_form")
public class PrematureForm extends AggregateRoot {

    @Id
    private UUID id;
    @Column(name = "admission_id", nullable = false)
    private UUID admissionId;
    @Column(name = "visit_id", nullable = false)
    private UUID visitId;
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "age_text", length = 60) private String ageText;
    @Column(name = "birth_weight_kg", precision = 5, scale = 3) private BigDecimal birthWeightKg;
    @Column(name = "birth_weight_date") private LocalDate birthWeightDate;
    @Column(name = "current_weight_kg", precision = 5, scale = 3) private BigDecimal currentWeightKg;
    @Column(name = "current_weight_date") private LocalDate currentWeightDate;
    @Column(name = "gestational_age_weeks") private Integer gestationalAgeWeeks;
    @Column(name = "gestational_age_days") private Integer gestationalAgeDays;
    @Column(name = "corrected_ga_weeks") private Integer correctedGaWeeks;
    @Column(name = "corrected_ga_days") private Integer correctedGaDays;
    @Column(name = "length_cm", precision = 5, scale = 2) private BigDecimal lengthCm;
    @Column(name = "length_date") private LocalDate lengthDate;
    @Column(name = "ofc_cm", precision = 5, scale = 2) private BigDecimal ofcCm;
    @Column(name = "ofc_date") private LocalDate ofcDate;
    @Column(name = "feeding_type", length = 120) private String feedingType;
    @Column(name = "kcal_per_oz", precision = 7, scale = 2) private BigDecimal kcalPerOz;
    @Column(name = "enteral_per_kg", precision = 7, scale = 2) private BigDecimal enteralPerKg;
    @Column(name = "kcal_per_kg", precision = 7, scale = 2) private BigDecimal kcalPerKg;
    @Column(precision = 7, scale = 2) private BigDecimal gir;
    @Column(name = "pharmacy_others", length = 2000) private String pharmacyOthers;
    @Column(name = "last_culture_date") private LocalDate lastCultureDate;
    @Column(name = "sample_type", length = 120) private String sampleType;
    @Column(name = "culture_result", length = 500) private String cultureResult;
    @Column(name = "prescription_notes", length = 2000) private String prescriptionNotes;
    @Column(name = "specialist_doctor_notes", length = 2000) private String specialistDoctorNotes;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "imageKey", column = @Column(name = "pharmacy_sign_key", length = 500)),
            @AttributeOverride(name = "signerName", column = @Column(name = "pharmacy_sign_name", length = 200)),
            @AttributeOverride(name = "signedBy", column = @Column(name = "pharmacy_signed_by")),
            @AttributeOverride(name = "signedAt", column = @Column(name = "pharmacy_signed_at")),
    })
    private Signature clinicalPharmacySignature = Signature.empty();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "imageKey", column = @Column(name = "resident_sign_key", length = 500)),
            @AttributeOverride(name = "signerName", column = @Column(name = "resident_sign_name", length = 200)),
            @AttributeOverride(name = "signedBy", column = @Column(name = "resident_signed_by")),
            @AttributeOverride(name = "signedAt", column = @Column(name = "resident_signed_at")),
    })
    private Signature residentSignature = Signature.empty();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "imageKey", column = @Column(name = "senior_sign_key", length = 500)),
            @AttributeOverride(name = "signerName", column = @Column(name = "senior_sign_name", length = 200)),
            @AttributeOverride(name = "signedBy", column = @Column(name = "senior_signed_by")),
            @AttributeOverride(name = "signedAt", column = @Column(name = "senior_signed_at")),
    })
    private Signature seniorResidentSignature = Signature.empty();

    public static PrematureForm create(UUID admissionId, UUID visitId, UUID patientId) {
        if (admissionId == null || visitId == null || patientId == null) {
            throw new DomainException("FORM_REFS_REQUIRED", "admission, visit and patient are required");
        }
        PrematureForm f = new PrematureForm();
        f.id = UUID.randomUUID();
        f.admissionId = admissionId;
        f.visitId = visitId;
        f.patientId = patientId;
        return f;
    }

    public void update(PrematureFormData d) {
        this.ageText = d.ageText();
        this.birthWeightKg = d.birthWeightKg();
        this.birthWeightDate = d.birthWeightDate();
        this.currentWeightKg = d.currentWeightKg();
        this.currentWeightDate = d.currentWeightDate();
        this.gestationalAgeWeeks = d.gestationalAgeWeeks();
        this.gestationalAgeDays = d.gestationalAgeDays();
        this.correctedGaWeeks = d.correctedGaWeeks();
        this.correctedGaDays = d.correctedGaDays();
        this.lengthCm = d.lengthCm();
        this.lengthDate = d.lengthDate();
        this.ofcCm = d.ofcCm();
        this.ofcDate = d.ofcDate();
        this.feedingType = d.feedingType();
        this.kcalPerOz = d.kcalPerOz();
        this.enteralPerKg = d.enteralPerKg();
        this.kcalPerKg = d.kcalPerKg();
        this.gir = d.gir();
        this.pharmacyOthers = d.pharmacyOthers();
        this.lastCultureDate = d.lastCultureDate();
        this.sampleType = d.sampleType();
        this.cultureResult = d.cultureResult();
        this.prescriptionNotes = d.prescriptionNotes();
        this.specialistDoctorNotes = d.specialistDoctorNotes();
    }

    public void applySignature(SignatureSlot slot, String imageKey, String signerName, UUID userId) {
        Signature sig = new Signature(imageKey, signerName, userId, Instant.now());
        switch (slot) {
            case CLINICAL_PHARMACY -> this.clinicalPharmacySignature = sig;
            case RESIDENT -> this.residentSignature = sig;
            case SENIOR_RESIDENT -> this.seniorResidentSignature = sig;
        }
    }

    public Signature signature(SignatureSlot slot) {
        return switch (slot) {
            case CLINICAL_PHARMACY -> clinicalPharmacySignature;
            case RESIDENT -> residentSignature;
            case SENIOR_RESIDENT -> seniorResidentSignature;
        };
    }
}
