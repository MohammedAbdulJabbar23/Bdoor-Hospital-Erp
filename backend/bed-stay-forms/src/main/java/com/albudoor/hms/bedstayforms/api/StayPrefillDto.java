package com.albudoor.hms.bedstayforms.api;

import com.albudoor.hms.bedstayforms.directory.StayInfo;

import java.time.Instant;
import java.util.UUID;

/** The auto fields the paper forms print: Pt. Name, Pt. Code (MRN), Age, DOA. Never stored. */
public record StayPrefillDto(UUID patientId, String patientName, String patientMrn,
                             String ageText, Instant admittedAt) {
    public static StayPrefillDto from(StayInfo i) {
        return new StayPrefillDto(i.patientId(), i.patientName(), i.patientMrn(), i.ageText(), i.admittedAt());
    }
}
