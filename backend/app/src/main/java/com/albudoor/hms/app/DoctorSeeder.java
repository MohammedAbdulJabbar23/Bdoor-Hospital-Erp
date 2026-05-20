package com.albudoor.hms.app;

import com.albudoor.hms.doctorappointment.domain.Doctor;
import com.albudoor.hms.doctorappointment.domain.WeeklyHour;
import com.albudoor.hms.doctorappointment.infrastructure.DoctorRepository;
import com.albudoor.hms.identity.domain.Role;
import com.albudoor.hms.identity.domain.User;
import com.albudoor.hms.identity.infrastructure.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Seeds three demo doctors on first boot — one general practitioner linked to the
 * existing {@code doctor} login and two consultants. Idempotent: only inserts doctors
 * whose user link / name doesn't already exist.
 */
@Component
@Order(20) // after DevDataSeeder (default order)
public class DoctorSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DoctorSeeder.class);

    private final DoctorRepository doctors;
    private final UserRepository users;

    public DoctorSeeder(DoctorRepository doctors, UserRepository users) {
        this.doctors = doctors;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (doctors.count() > 0) return;

        UUID drUserId = users.findByUsername("doctor").map(User::getId).orElse(null);
        UUID emergencyUserId = users.findByUsername("emergency").map(User::getId).orElse(null);
        UUID prematureUserId = users.findByUsername("premature").map(User::getId).orElse(null);

        // Iraqi clinic norm: working week Sun–Thu with split morning/evening blocks.
        List<WeeklyHour> generalHours = List.of(
                WeeklyHour.of(DayOfWeek.SUNDAY,    LocalTime.of(9, 0),  LocalTime.of(13, 0), 15),
                WeeklyHour.of(DayOfWeek.SUNDAY,    LocalTime.of(16, 0), LocalTime.of(20, 0), 15),
                WeeklyHour.of(DayOfWeek.MONDAY,    LocalTime.of(9, 0),  LocalTime.of(13, 0), 15),
                WeeklyHour.of(DayOfWeek.MONDAY,    LocalTime.of(16, 0), LocalTime.of(20, 0), 15),
                WeeklyHour.of(DayOfWeek.TUESDAY,   LocalTime.of(9, 0),  LocalTime.of(13, 0), 15),
                WeeklyHour.of(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0),  LocalTime.of(13, 0), 15),
                WeeklyHour.of(DayOfWeek.WEDNESDAY, LocalTime.of(16, 0), LocalTime.of(20, 0), 15),
                WeeklyHour.of(DayOfWeek.THURSDAY,  LocalTime.of(9, 0),  LocalTime.of(13, 0), 15)
        );

        List<WeeklyHour> consultantHours = List.of(
                WeeklyHour.of(DayOfWeek.SUNDAY,    LocalTime.of(10, 0), LocalTime.of(14, 0), 30),
                WeeklyHour.of(DayOfWeek.TUESDAY,   LocalTime.of(10, 0), LocalTime.of(14, 0), 30),
                WeeklyHour.of(DayOfWeek.THURSDAY,  LocalTime.of(10, 0), LocalTime.of(14, 0), 30)
        );

        seedDoctor(drUserId, "Dr. Kareem Al-Janabi",  "General Practitioner",  new BigDecimal("15000"), "+9647701111111", generalHours);
        seedDoctor(emergencyUserId, "Dr. Hassan Al-Obeidi", "Emergency Medicine", new BigDecimal("20000"), "+9647702222222", consultantHours);
        seedDoctor(prematureUserId, "Dr. Noor Al-Rubaie", "Neonatology",       new BigDecimal("25000"), "+9647703333333", consultantHours);

        log.warn("DoctorSeeder: created 3 demo doctors with default schedules. Adjust in /admin/doctors before production.");
    }

    private void seedDoctor(UUID userId, String fullName, String specialty, BigDecimal fee, String phone, List<WeeklyHour> hours) {
        if (doctors.findAll().stream().anyMatch(d -> d.getFullName().equalsIgnoreCase(fullName))) return;
        if (userId != null && doctors.findByUserId(userId).isPresent()) return;
        Doctor d = Doctor.create(userId, fullName, specialty, fee, "IQD", phone);
        d.replaceSchedule(hours);
        doctors.save(d);
        // Verify roles for sanity (no-op if user is missing/already-set).
        if (userId != null) {
            users.findById(userId).ifPresent(u -> {
                if (!u.getRoles().contains(Role.DOCTOR)) {
                    log.info("Doctor {} linked to user {} which lacks DOCTOR role", fullName, u.getUsername());
                }
            });
        }
    }
}
