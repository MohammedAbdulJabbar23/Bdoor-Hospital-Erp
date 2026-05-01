package com.albudoor.hms.patientregistry.registernewpatient;

import com.albudoor.hms.patientregistry.domain.AdultDetails;
import com.albudoor.hms.patientregistry.domain.Gender;
import com.albudoor.hms.patientregistry.domain.InfantDetails;
import com.albudoor.hms.patientregistry.domain.Patient;
import com.albudoor.hms.patientregistry.domain.PatientType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record PatientResponse(
        UUID id,
        String mrn,
        PatientType type,
        String fullName,
        Gender gender,
        LocalDate dateOfBirth,
        boolean vip,
        boolean archived,
        AdultPart adult,
        InfantPart infant
) {

    public record AdultPart(
            String nationalId,
            String mobileNumber,
            String address,
            String occupation,
            String emergencyContactName,
            String emergencyContactMobile
    ) {}

    public record InfantPart(
            UUID motherPatientId,
            String motherName,
            String motherNationalId,
            String motherMobile,
            String fatherName,
            String fatherMobile,
            LocalTime dobTime,
            String placeOfBirth,
            String deliveryType,
            Integer apgar1Min,
            Integer apgar5Min,
            BigDecimal birthWeightKg,
            BigDecimal lengthCm,
            BigDecimal ofcCm,
            Integer gestationalAgeWeeks,
            Integer gestationalAgeDays,
            String guardianName,
            String guardianRelationship,
            String guardianMobile,
            String guardianNationalId
    ) {}

    public static PatientResponse from(Patient p) {
        AdultPart adult = null;
        if (p.getAdultDetails() != null) {
            AdultDetails d = p.getAdultDetails();
            adult = new AdultPart(
                    d.getNationalId(), d.getMobileNumber(), d.getAddress(),
                    d.getOccupation(), d.getEmergencyContactName(), d.getEmergencyContactMobile()
            );
        }
        InfantPart infant = null;
        if (p.getInfantDetails() != null) {
            InfantDetails d = p.getInfantDetails();
            infant = new InfantPart(
                    d.getMotherPatientId(), d.getMotherName(), d.getMotherNationalId(), d.getMotherMobile(),
                    d.getFatherName(), d.getFatherMobile(), d.getDobTime(),
                    d.getPlaceOfBirth() != null ? d.getPlaceOfBirth().name() : null,
                    d.getDeliveryType() != null ? d.getDeliveryType().name() : null,
                    d.getApgar1Min(), d.getApgar5Min(),
                    d.getBirthWeightKg(), d.getLengthCm(), d.getOfcCm(),
                    d.getGestationalAgeWeeks(), d.getGestationalAgeDays(),
                    d.getGuardianName(), d.getGuardianRelationship(),
                    d.getGuardianMobile(), d.getGuardianNationalId()
            );
        }
        return new PatientResponse(
                p.getId(), p.getMrn(), p.getType(),
                p.getFullName(), p.getGender(), p.getDateOfBirth(),
                p.isVip(), p.isArchived(),
                adult, infant
        );
    }
}
