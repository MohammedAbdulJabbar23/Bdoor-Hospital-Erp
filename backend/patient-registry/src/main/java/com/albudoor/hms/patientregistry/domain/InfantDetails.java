package com.albudoor.hms.patientregistry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Infant-specific intake fields. Mirrors HMS-BRD-REC-005 §3.2:
 * "Patient being registered is the premature infant; parent/guardian provides data."
 *
 * National ID is intentionally absent — Iraqi infants typically receive registration later.
 * Identification is anchored to mother + guardian.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class InfantDetails {

    @Column(name = "mother_patient_id")
    private UUID motherPatientId;

    @Column(name = "mother_name", length = 200)
    private String motherName;

    @Column(name = "mother_national_id", length = 50)
    private String motherNationalId;

    @Column(name = "mother_mobile", length = 30)
    private String motherMobile;

    @Column(name = "father_name", length = 200)
    private String fatherName;

    @Column(name = "father_mobile", length = 30)
    private String fatherMobile;

    @Column(name = "dob_time")
    private LocalTime dobTime;

    @Column(name = "place_of_birth", length = 30)
    @Enumerated(EnumType.STRING)
    private PlaceOfBirth placeOfBirth;

    @Column(name = "delivery_type", length = 30)
    @Enumerated(EnumType.STRING)
    private DeliveryType deliveryType;

    @Column(name = "apgar_1min")
    private Integer apgar1Min;

    @Column(name = "apgar_5min")
    private Integer apgar5Min;

    @Column(name = "birth_weight_kg", precision = 5, scale = 3)
    private BigDecimal birthWeightKg;

    @Column(name = "length_cm", precision = 5, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "ofc_cm", precision = 5, scale = 2)
    private BigDecimal ofcCm;

    @Column(name = "gestational_age_weeks")
    private Integer gestationalAgeWeeks;

    @Column(name = "gestational_age_days")
    private Integer gestationalAgeDays;

    @Column(name = "guardian_name", length = 200)
    private String guardianName;

    @Column(name = "guardian_relationship", length = 100)
    private String guardianRelationship;

    @Column(name = "guardian_mobile", length = 30)
    private String guardianMobile;

    @Column(name = "guardian_national_id", length = 50)
    private String guardianNationalId;
}
