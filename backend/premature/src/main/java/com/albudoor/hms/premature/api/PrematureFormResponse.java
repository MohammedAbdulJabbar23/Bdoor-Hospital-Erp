package com.albudoor.hms.premature.api;

import com.albudoor.hms.premature.domain.PrematureForm;
import com.albudoor.hms.premature.domain.Signature;
import com.albudoor.hms.premature.domain.SignatureSlot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PrematureFormResponse(
        UUID id, UUID admissionId,
        String ageText,
        BigDecimal birthWeightKg, LocalDate birthWeightDate,
        BigDecimal currentWeightKg, LocalDate currentWeightDate,
        Integer gestationalAgeWeeks, Integer gestationalAgeDays,
        Integer correctedGaWeeks, Integer correctedGaDays,
        BigDecimal lengthCm, LocalDate lengthDate, BigDecimal ofcCm, LocalDate ofcDate,
        String feedingType, BigDecimal kcalPerOz, BigDecimal enteralPerKg, BigDecimal kcalPerKg,
        BigDecimal gir, String pharmacyOthers,
        LocalDate lastCultureDate, String sampleType, String cultureResult,
        String prescriptionNotes, String specialistDoctorNotes,
        Sig clinicalPharmacySignature, Sig residentSignature, Sig seniorResidentSignature
) {
    public record Sig(boolean present, String signerName, UUID signedBy, Instant signedAt) {
        static Sig from(Signature s) {
            boolean present = s != null && s.getImageKey() != null;
            return new Sig(present, s == null ? null : s.getSignerName(),
                    s == null ? null : s.getSignedBy(), s == null ? null : s.getSignedAt());
        }
    }

    public static PrematureFormResponse from(PrematureForm f) {
        return new PrematureFormResponse(
                f.getId(), f.getAdmissionId(), f.getAgeText(),
                f.getBirthWeightKg(), f.getBirthWeightDate(), f.getCurrentWeightKg(), f.getCurrentWeightDate(),
                f.getGestationalAgeWeeks(), f.getGestationalAgeDays(), f.getCorrectedGaWeeks(), f.getCorrectedGaDays(),
                f.getLengthCm(), f.getLengthDate(), f.getOfcCm(), f.getOfcDate(),
                f.getFeedingType(), f.getKcalPerOz(), f.getEnteralPerKg(), f.getKcalPerKg(), f.getGir(),
                f.getPharmacyOthers(), f.getLastCultureDate(), f.getSampleType(), f.getCultureResult(),
                f.getPrescriptionNotes(), f.getSpecialistDoctorNotes(),
                Sig.from(f.signature(SignatureSlot.CLINICAL_PHARMACY)),
                Sig.from(f.signature(SignatureSlot.RESIDENT)),
                Sig.from(f.signature(SignatureSlot.SENIOR_RESIDENT)));
    }
}
