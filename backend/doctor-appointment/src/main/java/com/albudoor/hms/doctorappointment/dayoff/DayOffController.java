package com.albudoor.hms.doctorappointment.dayoff;

import com.albudoor.hms.doctorappointment.api.DoctorResponse;
import com.albudoor.hms.doctorappointment.domain.DayOff;
import com.albudoor.hms.doctorappointment.domain.Doctor;
import com.albudoor.hms.doctorappointment.infrastructure.DoctorRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/doctors")
public class DayOffController {

    private final DoctorRepository repo;

    public DayOffController(DoctorRepository repo) {
        this.repo = repo;
    }

    public record AddDayOff(@NotNull LocalDate date, String reason) {}

    @PostMapping("/{id}/days-off")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @Transactional
    public DoctorResponse addDayOff(@PathVariable UUID id, @Valid @RequestBody AddDayOff body) {
        Doctor doctor = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + id));
        doctor.addDayOff(new DayOff(body.date(), body.reason()));
        return DoctorResponse.from(doctor);
    }

    @DeleteMapping("/{id}/days-off/{date}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @Transactional
    public DoctorResponse removeDayOff(@PathVariable UUID id, @PathVariable LocalDate date) {
        Doctor doctor = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + id));
        doctor.removeDayOff(date);
        return DoctorResponse.from(doctor);
    }
}
