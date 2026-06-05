package com.albudoor.hms.doctorappointment.api;

import com.albudoor.hms.doctorappointment.domain.Appointment;
import com.albudoor.hms.doctorappointment.domain.AppointmentSlot;
import com.albudoor.hms.doctorappointment.domain.AppointmentStatus;
import com.albudoor.hms.doctorappointment.domain.DayOff;
import com.albudoor.hms.doctorappointment.domain.Doctor;
import com.albudoor.hms.doctorappointment.domain.WeeklyHour;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes available appointment slots for a doctor on a given date.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>If the date is in {@code daysOff}, return empty list.</li>
 *   <li>For every {@link WeeklyHour} matching the date's day-of-week, generate slots
 *       from {@code startTime} to {@code endTime} stepping by {@code slotMinutes}.</li>
 *   <li>Mark slots as unavailable if a non-cancelled appointment already starts there.</li>
 * </ol>
 */
@Service
public class SlotComputationService {

    public List<AppointmentSlot> compute(
            Doctor doctor, LocalDate date, List<Appointment> existingAppointments
    ) {
        if (!doctor.isActive()) return List.of();

        boolean isDayOff = doctor.getDaysOff().stream()
                .map(DayOff::getDate)
                .anyMatch(d -> d.equals(date));
        if (isDayOff) return List.of();

        Set<LocalDateTime> takenStarts = existingAppointments.stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED
                        && a.getStatus() != AppointmentStatus.NO_SHOW)
                .map(Appointment::getScheduledFor)
                .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        List<AppointmentSlot> slots = new ArrayList<>();
        for (WeeklyHour wh : doctor.getWeeklyHours()) {
            if (wh.getDayOfWeek() != date.getDayOfWeek()) continue;

            int slotMin = wh.getSlotMinutes();
            if (slotMin <= 0) continue; // defensive: a non-positive step would never terminate

            // Iterate on LocalDateTime, NOT LocalTime: LocalTime.plusMinutes() WRAPS past
            // midnight, so a late-ending block (…→23:30→00:00) made this loop forever.
            // LocalDateTime advances the calendar day, so the end check always terminates.
            LocalDateTime start = LocalDateTime.of(date, wh.getStartTime());
            LocalDateTime blockEnd = LocalDateTime.of(date, wh.getEndTime());
            while (!start.plusMinutes(slotMin).isAfter(blockEnd)) {
                // A slot is bookable only if no live appointment holds it AND it has not
                // already elapsed (a slot whose start is in the past can never be booked).
                boolean available = !takenStarts.contains(start) && !start.isBefore(now);
                slots.add(new AppointmentSlot(start, slotMin, available));
                start = start.plusMinutes(slotMin);
            }
        }
        return slots;
    }
}
