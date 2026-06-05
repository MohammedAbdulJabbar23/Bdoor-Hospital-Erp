package com.albudoor.hms.doctorappointment.api;

import com.albudoor.hms.doctorappointment.domain.AppointmentSlot;
import com.albudoor.hms.doctorappointment.domain.Doctor;
import com.albudoor.hms.doctorappointment.domain.WeeklyHour;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class SlotComputationServiceTest {

    private final SlotComputationService svc = new SlotComputationService();

    private Doctor doctorWith(LocalDate date, LocalTime start, LocalTime end, int slot) {
        Doctor d = Doctor.create(UUID.randomUUID(), "Dr. Test", "GP",
                new BigDecimal("100"), "IQD", "+9647700000000");
        d.replaceSchedule(List.of(WeeklyHour.of(date.getDayOfWeek(), start, end, slot)));
        return d;
    }

    /** Regression: a late-ending block (cursor would wrap past midnight) must NOT hang. */
    @Test
    void lateEndingSchedule_terminates_and_doesNotHang() {
        LocalDate date = LocalDate.of(2026, 6, 10);
        Doctor d = doctorWith(date, LocalTime.of(22, 0), LocalTime.of(23, 59), 30);

        List<AppointmentSlot> slots = assertTimeoutPreemptively(Duration.ofSeconds(3),
                () -> svc.compute(d, date, List.of()),
                "slot computation hung (LocalTime wrap-around infinite loop)");

        // 22:00, 22:30, 23:00 — the 23:30 slot would end at 00:00 (after the 23:59 block end), so excluded.
        assertThat(slots).hasSize(3);
        assertThat(slots.get(0).startsAt().toLocalTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(slots.get(slots.size() - 1).startsAt().toLocalTime()).isEqualTo(LocalTime.of(23, 0));
    }

    /** Normal daytime schedule is unchanged. */
    @Test
    void normalSchedule_producesExpectedSlots() {
        LocalDate date = LocalDate.of(2026, 6, 10);
        Doctor d = doctorWith(date, LocalTime.of(9, 0), LocalTime.of(12, 0), 30);

        List<AppointmentSlot> slots = assertTimeoutPreemptively(Duration.ofSeconds(3),
                () -> svc.compute(d, date, List.of()));

        // 09:00 … 11:30 (11:30+30 = 12:00 == end, included) => 6 slots
        assertThat(slots).hasSize(6);
        assertThat(slots.get(0).startsAt().toLocalTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(slots.get(5).startsAt().toLocalTime()).isEqualTo(LocalTime.of(11, 30));
    }
}
