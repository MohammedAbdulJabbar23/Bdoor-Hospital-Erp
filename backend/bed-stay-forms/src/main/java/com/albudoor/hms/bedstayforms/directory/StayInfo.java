package com.albudoor.hms.bedstayforms.directory;

import java.time.Instant;
import java.util.UUID;

public record StayInfo(
        UUID patientId, String patientName, String patientMrn,
        String ageText, Instant admittedAt, boolean open
) {}
