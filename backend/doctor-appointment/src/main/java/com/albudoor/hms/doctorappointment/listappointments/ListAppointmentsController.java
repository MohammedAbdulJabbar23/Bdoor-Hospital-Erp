package com.albudoor.hms.doctorappointment.listappointments;

import com.albudoor.hms.doctorappointment.api.AppointmentResponse;
import com.albudoor.hms.doctorappointment.domain.Appointment;
import com.albudoor.hms.doctorappointment.infrastructure.AppointmentRepository;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
@PreAuthorize("isAuthenticated()")
public class ListAppointmentsController {

    private final AppointmentRepository repo;

    public ListAppointmentsController(AppointmentRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(
            @RequestParam("doctorId") UUID doctorId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return repo.findAllByDoctorIdAndScheduledDateOrderByScheduledForAsc(doctorId, date).stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public AppointmentResponse byId(@PathVariable UUID id) {
        Appointment a = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Appointment not found: " + id));
        return AppointmentResponse.from(a);
    }

    @GetMapping("/by-patient/{patientId}")
    @Transactional(readOnly = true)
    public List<AppointmentResponse> byPatient(@PathVariable UUID patientId) {
        return repo.findAllByPatientIdOrderByScheduledForDesc(patientId).stream()
                .map(AppointmentResponse::from)
                .toList();
    }
}
