package com.albudoor.hms.patientregistry.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The Patient aggregate. One MRN, immortal record, archived not deleted.
 * Two intake profiles: ADULT (GDF) and INFANT (Premature intake form).
 * VIP is a patient-level flag set by reception; bypasses ALL payments globally.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "patient", uniqueConstraints = {
        @UniqueConstraint(name = "uk_patient_mrn", columnNames = "mrn")
})
public class Patient extends AggregateRoot {

    @Id
    private UUID id;

    @Column(nullable = false, length = 30)
    private String mrn;

    @Enumerated(EnumType.STRING)
    @Column(name = "patient_type", nullable = false, length = 20)
    private PatientType type;

    @Column(name = "full_name", nullable = false, length = 300)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Embedded
    private AdultDetails adultDetails;

    @Embedded
    private InfantDetails infantDetails;

    @Column(name = "is_vip", nullable = false)
    private boolean vip;

    @Column(nullable = false)
    private boolean archived;

    public static Patient registerAdult(
            String mrn,
            String fullName,
            Gender gender,
            LocalDate dateOfBirth,
            AdultDetails details,
            boolean vip
    ) {
        validateCommon(mrn, fullName, gender, dateOfBirth);
        if (details == null) {
            throw new DomainException("PATIENT_DETAILS_REQUIRED", "Adult details are required");
        }
        Patient p = new Patient();
        p.id = UUID.randomUUID();
        p.mrn = mrn;
        p.type = PatientType.ADULT;
        p.fullName = fullName.trim();
        p.gender = gender;
        p.dateOfBirth = dateOfBirth;
        p.adultDetails = details;
        p.vip = vip;
        p.archived = false;
        p.registerEvent(PatientRegisteredEvent.of(p.id, p.mrn, p.type));
        return p;
    }

    public static Patient registerInfant(
            String mrn,
            String fullName,
            Gender gender,
            LocalDate dateOfBirth,
            InfantDetails details,
            boolean vip
    ) {
        validateCommon(mrn, fullName, gender, dateOfBirth);
        if (details == null) {
            throw new DomainException("INFANT_DETAILS_REQUIRED", "Infant details are required");
        }
        if (details.getMotherPatientId() == null && (details.getMotherName() == null || details.getMotherName().isBlank())) {
            throw new DomainException(
                    "INFANT_MOTHER_REQUIRED",
                    "Either mother patient reference or mother name is required");
        }
        if (details.getGuardianName() == null || details.getGuardianName().isBlank()) {
            throw new DomainException("INFANT_GUARDIAN_REQUIRED", "Guardian name is required");
        }
        Patient p = new Patient();
        p.id = UUID.randomUUID();
        p.mrn = mrn;
        p.type = PatientType.INFANT;
        p.fullName = fullName.trim();
        p.gender = gender;
        p.dateOfBirth = dateOfBirth;
        p.infantDetails = details;
        p.vip = vip;
        p.archived = false;
        p.registerEvent(PatientRegisteredEvent.of(p.id, p.mrn, p.type));
        return p;
    }

    public void setVip(boolean vip) {
        this.vip = vip;
    }

    public void archive() {
        this.archived = true;
    }

    public void unarchive() {
        this.archived = false;
    }

    private static void validateCommon(String mrn, String fullName, Gender gender, LocalDate dob) {
        if (mrn == null || mrn.isBlank()) {
            throw new DomainException("PATIENT_MRN_REQUIRED", "MRN is required");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new DomainException("PATIENT_NAME_REQUIRED", "Full name is required");
        }
        if (gender == null) {
            throw new DomainException("PATIENT_GENDER_REQUIRED", "Gender is required");
        }
        if (dob == null) {
            throw new DomainException("PATIENT_DOB_REQUIRED", "Date of birth is required");
        }
        if (dob.isAfter(LocalDate.now())) {
            throw new DomainException("PATIENT_DOB_FUTURE", "Date of birth cannot be in the future");
        }
    }
}
