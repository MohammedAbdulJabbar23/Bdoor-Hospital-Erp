package com.albudoor.hms.doctorappointment.domain;

import java.time.LocalDateTime;

/**
 * A computed time slot for a given doctor on a given date. Not persisted — derived from
 * {@link Doctor#getWeeklyHours()} minus existing appointments and days off.
 */
public record AppointmentSlot(
        LocalDateTime startsAt,
        int durationMinutes,
        boolean available
) {
    public LocalDateTime endsAt() {
        return startsAt.plusMinutes(durationMinutes);
    }
}
