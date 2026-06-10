package com.albudoor.hms.bedstayforms.api;

import com.albudoor.hms.bedstayforms.domain.MedicalHistorySheet;
import com.albudoor.hms.bedstayforms.domain.MhSignatureSlot;

import java.math.BigDecimal;

public record MedicalHistoryDto(
        BigDecimal weightKg, BigDecimal heightCm, String doctorName,
        String chiefComplaint, String presentIllnessHx, String psHx, String pmHx,
        String familyHx, String allergicHx,
        String socialSmoker, String socialAlcohol, String socialSleep,
        String drugHx, String physicalExamination,
        SignatureView specialistSignature, SignatureView permanentSignature, SignatureView residentSignature
) {
    public static MedicalHistoryDto from(MedicalHistorySheet s) {
        return new MedicalHistoryDto(
                s.getWeightKg(), s.getHeightCm(), s.getDoctorName(),
                s.getChiefComplaint(), s.getPresentIllnessHx(), s.getPsHx(), s.getPmHx(),
                s.getFamilyHx(), s.getAllergicHx(),
                s.getSocialSmoker(), s.getSocialAlcohol(), s.getSocialSleep(),
                s.getDrugHx(), s.getPhysicalExamination(),
                SignatureView.from(s.signature(MhSignatureSlot.SPECIALIST)),
                SignatureView.from(s.signature(MhSignatureSlot.PERMANENT)),
                SignatureView.from(s.signature(MhSignatureSlot.RESIDENT)));
    }
}
