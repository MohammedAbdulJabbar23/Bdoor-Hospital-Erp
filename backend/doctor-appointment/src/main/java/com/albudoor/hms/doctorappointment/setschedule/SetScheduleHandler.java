package com.albudoor.hms.doctorappointment.setschedule;

import com.albudoor.hms.doctorappointment.domain.Doctor;
import com.albudoor.hms.doctorappointment.domain.WeeklyHour;
import com.albudoor.hms.doctorappointment.infrastructure.DoctorRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SetScheduleHandler {

    private final DoctorRepository repo;

    public SetScheduleHandler(DoctorRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Doctor handle(UUID doctorId, SetScheduleCommand cmd) {
        Doctor doctor = repo.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + doctorId));
        List<WeeklyHour> hours = cmd.blocks().stream()
                .map(b -> WeeklyHour.of(b.dayOfWeek(), b.startTime(), b.endTime(), b.slotMinutes()))
                .toList();
        doctor.replaceSchedule(hours);
        return doctor;
    }
}
