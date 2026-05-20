package com.albudoor.hms.doctorappointment.domain;

import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * One block of a doctor's weekly schedule. A typical Iraqi clinic doctor has 1–2 blocks
 * per working day (e.g. morning + evening). Stored as an {@code @ElementCollection} on
 * {@link Doctor}.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class WeeklyHour {

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_minutes", nullable = false)
    private Integer slotMinutes;

    public static WeeklyHour of(DayOfWeek dow, LocalTime start, LocalTime end, int slotMinutes) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new DomainException("INVALID_HOURS",
                    "endTime must be after startTime for " + dow);
        }
        if (slotMinutes <= 0 || slotMinutes > 240) {
            throw new DomainException("INVALID_SLOT_LENGTH",
                    "slot length must be between 1 and 240 minutes");
        }
        return new WeeklyHour(dow, start, end, slotMinutes);
    }
}
