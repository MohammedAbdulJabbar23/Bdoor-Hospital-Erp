package com.albudoor.hms.bedstayforms.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** BRD REC-005 §6.6.1 — Medical History and Physical Examination Sheet, one per bed-stay. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stay_medical_history")
public class MedicalHistorySheet extends AggregateRoot {

    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StayDepartment department;
    @Column(name = "stay_id", nullable = false)
    private UUID stayId;

    @Column(name = "weight_kg", precision = 6, scale = 2) private BigDecimal weightKg;
    @Column(name = "height_cm", precision = 6, scale = 2) private BigDecimal heightCm;
    @Column(name = "doctor_name", length = 200) private String doctorName;
    @Column(name = "chief_complaint", length = 2000) private String chiefComplaint;
    @Column(name = "present_illness_hx", length = 4000) private String presentIllnessHx;
    @Column(name = "ps_hx", length = 2000) private String psHx;
    @Column(name = "pm_hx", length = 2000) private String pmHx;
    @Column(name = "family_hx", length = 2000) private String familyHx;
    @Column(name = "allergic_hx", length = 2000) private String allergicHx;
    @Column(name = "social_smoker", length = 200) private String socialSmoker;
    @Column(name = "social_alcohol", length = 200) private String socialAlcohol;
    @Column(name = "social_sleep", length = 200) private String socialSleep;
    @Column(name = "drug_hx", length = 2000) private String drugHx;
    @Column(name = "physical_examination", length = 4000) private String physicalExamination;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "imageKey", column = @Column(name = "specialist_sign_key", length = 500)),
            @AttributeOverride(name = "signerName", column = @Column(name = "specialist_sign_name", length = 200)),
            @AttributeOverride(name = "signedBy", column = @Column(name = "specialist_signed_by")),
            @AttributeOverride(name = "signedAt", column = @Column(name = "specialist_signed_at")),
    })
    private FormSignature specialistSignature = FormSignature.empty();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "imageKey", column = @Column(name = "permanent_sign_key", length = 500)),
            @AttributeOverride(name = "signerName", column = @Column(name = "permanent_sign_name", length = 200)),
            @AttributeOverride(name = "signedBy", column = @Column(name = "permanent_signed_by")),
            @AttributeOverride(name = "signedAt", column = @Column(name = "permanent_signed_at")),
    })
    private FormSignature permanentSignature = FormSignature.empty();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "imageKey", column = @Column(name = "resident_sign_key", length = 500)),
            @AttributeOverride(name = "signerName", column = @Column(name = "resident_sign_name", length = 200)),
            @AttributeOverride(name = "signedBy", column = @Column(name = "resident_signed_by")),
            @AttributeOverride(name = "signedAt", column = @Column(name = "resident_signed_at")),
    })
    private FormSignature residentSignature = FormSignature.empty();

    public static MedicalHistorySheet create(StayDepartment department, UUID stayId) {
        if (department == null || stayId == null) {
            throw new DomainException("STAY_REF_REQUIRED", "department and stay are required");
        }
        MedicalHistorySheet s = new MedicalHistorySheet();
        s.id = UUID.randomUUID();
        s.department = department;
        s.stayId = stayId;
        return s;
    }

    public void update(MedicalHistoryData d) {
        this.weightKg = d.weightKg();
        this.heightCm = d.heightCm();
        this.doctorName = d.doctorName();
        this.chiefComplaint = d.chiefComplaint();
        this.presentIllnessHx = d.presentIllnessHx();
        this.psHx = d.psHx();
        this.pmHx = d.pmHx();
        this.familyHx = d.familyHx();
        this.allergicHx = d.allergicHx();
        this.socialSmoker = d.socialSmoker();
        this.socialAlcohol = d.socialAlcohol();
        this.socialSleep = d.socialSleep();
        this.drugHx = d.drugHx();
        this.physicalExamination = d.physicalExamination();
    }

    public void applySignature(MhSignatureSlot slot, String imageKey, String signerName, UUID signedBy) {
        FormSignature s = new FormSignature(imageKey, signerName, signedBy, Instant.now());
        switch (slot) {
            case SPECIALIST -> this.specialistSignature = s;
            case PERMANENT -> this.permanentSignature = s;
            case RESIDENT -> this.residentSignature = s;
        }
    }

    public FormSignature signature(MhSignatureSlot slot) {
        return switch (slot) {
            case SPECIALIST -> specialistSignature == null ? FormSignature.empty() : specialistSignature;
            case PERMANENT -> permanentSignature == null ? FormSignature.empty() : permanentSignature;
            case RESIDENT -> residentSignature == null ? FormSignature.empty() : residentSignature;
        };
    }
}
