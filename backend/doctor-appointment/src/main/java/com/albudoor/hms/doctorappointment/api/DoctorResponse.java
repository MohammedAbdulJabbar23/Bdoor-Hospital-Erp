package com.albudoor.hms.doctorappointment.api;

import com.albudoor.hms.doctorappointment.domain.DayOff;
import com.albudoor.hms.doctorappointment.domain.Doctor;
import com.albudoor.hms.doctorappointment.domain.WeeklyHour;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record DoctorResponse(
        UUID id,
        UUID userId,
        String fullName,
        String specialty,
        BigDecimal consultationFee,
        String currency,
        String phone,
        boolean active,
        List<WeeklyHourPart> weeklyHours,
        List<DayOffPart> daysOff
) {
    public record WeeklyHourPart(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, int slotMinutes) {}
    public record DayOffPart(LocalDate date, String reason) {}

    public static DoctorResponse from(Doctor d) {
        List<WeeklyHourPart> hours = d.getWeeklyHours().stream()
                .map(w -> new WeeklyHourPart(w.getDayOfWeek(), w.getStartTime(), w.getEndTime(), w.getSlotMinutes()))
                .toList();
        List<DayOffPart> off = d.getDaysOff().stream()
                .map(o -> new DayOffPart(o.getDate(), o.getReason()))
                .toList();
        return new DoctorResponse(
                d.getId(), d.getUserId(), d.getFullName(), d.getSpecialty(),
                d.getConsultationFee(), d.getCurrency(), d.getPhone(), d.isActive(),
                hours, off);
    }
}
