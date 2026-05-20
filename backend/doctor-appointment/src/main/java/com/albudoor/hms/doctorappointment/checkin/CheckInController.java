package com.albudoor.hms.doctorappointment.checkin;

import com.albudoor.hms.doctorappointment.api.AppointmentResponse;
import com.albudoor.hms.doctorappointment.domain.Appointment;
import com.albudoor.hms.doctorappointment.infrastructure.AppointmentRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
public class CheckInController {

    private final AppointmentRepository repo;
    private final ApplicationEventPublisher events;

    public CheckInController(AppointmentRepository repo, ApplicationEventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN', 'DOCTOR')")
    @Transactional
    public AppointmentResponse checkIn(@PathVariable UUID id) {
        Appointment a = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Appointment not found: " + id));
        a.checkIn();
        a.pullDomainEvents().forEach(events::publishEvent);
        return AppointmentResponse.from(a);
    }
}
