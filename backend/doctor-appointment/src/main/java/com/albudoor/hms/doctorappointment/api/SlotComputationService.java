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

        List<AppointmentSlot> slots = new ArrayList<>();
        for (WeeklyHour wh : doctor.getWeeklyHours()) {
            if (wh.getDayOfWeek() != date.getDayOfWeek()) continue;

            LocalTime cursor = wh.getStartTime();
            int slotMin = wh.getSlotMinutes();
            while (cursor.plusMinutes(slotMin).compareTo(wh.getEndTime()) <= 0) {
                LocalDateTime start = LocalDateTime.of(date, cursor);
                boolean available = !takenStarts.contains(start);
                slots.add(new AppointmentSlot(start, slotMin, available));
                cursor = cursor.plusMinutes(slotMin);
            }
        }
        return slots;
    }
}
