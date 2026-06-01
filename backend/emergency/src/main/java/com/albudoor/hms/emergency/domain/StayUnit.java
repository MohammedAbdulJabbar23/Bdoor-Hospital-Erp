package com.albudoor.hms.emergency.domain;

import java.time.temporal.ChronoUnit;

public enum StayUnit {
    HOURS(ChronoUnit.HOURS),
    DAYS(ChronoUnit.DAYS);

    private final ChronoUnit chronoUnit;

    StayUnit(ChronoUnit chronoUnit) {
        this.chronoUnit = chronoUnit;
    }

    public ChronoUnit chronoUnit() {
        return chronoUnit;
    }
}
