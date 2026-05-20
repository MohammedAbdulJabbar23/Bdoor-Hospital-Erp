package com.albudoor.hms.doctorappointment.listslots;

import com.albudoor.hms.doctorappointment.api.SlotComputationService;
import com.albudoor.hms.doctorappointment.domain.Appointment;
import com.albudoor.hms.doctorappointment.domain.AppointmentSlot;
import com.albudoor.hms.doctorappointment.domain.AppointmentStatus;
import com.albudoor.hms.doctorappointment.domain.Doctor;
import com.albudoor.hms.doctorappointment.infrastructure.AppointmentRepository;
import com.albudoor.hms.doctorappointment.infrastructure.DoctorRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/doctors")
@PreAuthorize("isAuthenticated()")
public class ListSlotsController {

    public record SlotResponse(LocalDateTime startsAt, LocalDateTime endsAt, int durationMinutes, boolean available) {
        static SlotResponse from(AppointmentSlot s) {
            return new SlotResponse(s.startsAt(), s.endsAt(), s.durationMinutes(), s.available());
        }
    }

    private final DoctorRepository doctors;
    private final AppointmentRepository appointments;
    private final SlotComputationService computer;

    public ListSlotsController(
            DoctorRepository doctors, AppointmentRepository appointments, SlotComputationService computer
    ) {
        this.doctors = doctors;
        this.appointments = appointments;
        this.computer = computer;
    }

    @GetMapping("/{id}/slots")
    @Transactional(readOnly = true)
    public List<SlotResponse> slots(
            @PathVariable UUID id,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        Doctor doctor = doctors.findById(id)
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + id));
        List<Appointment> existing = appointments.findActiveByDoctorAndDate(
                doctor.getId(), date, AppointmentStatus.CANCELLED);
        return computer.compute(doctor, date, existing).stream()
                .map(SlotResponse::from)
                .toList();
    }
}
