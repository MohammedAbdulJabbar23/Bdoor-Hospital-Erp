package com.albudoor.hms.bedstayforms.directory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Human-readable age at admission, e.g. "12 days" / "3 weeks". */
public final class AgeText {
    private AgeText() {}

    public static String derive(LocalDate dob, LocalDate at) {
        if (dob == null || at == null || at.isBefore(dob)) return null;
        long days = ChronoUnit.DAYS.between(dob, at);
        if (days < 14) return days + (days == 1 ? " day" : " days");
        return (days / 7) + " weeks";
    }
}
