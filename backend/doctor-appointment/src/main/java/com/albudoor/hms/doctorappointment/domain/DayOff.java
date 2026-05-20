package com.albudoor.hms.doctorappointment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** A specific date a doctor is unavailable, overriding the weekly schedule. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DayOff {

    @Column(nullable = false)
    private LocalDate date;

    @Column(length = 200)
    private String reason;
}
