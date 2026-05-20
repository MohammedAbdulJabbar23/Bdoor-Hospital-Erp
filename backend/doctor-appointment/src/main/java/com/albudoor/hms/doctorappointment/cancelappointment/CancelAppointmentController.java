package com.albudoor.hms.doctorappointment.cancelappointment;

import com.albudoor.hms.doctorappointment.api.AppointmentResponse;
import com.albudoor.hms.doctorappointment.domain.Appointment;
import com.albudoor.hms.doctorappointment.infrastructure.AppointmentRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
public class CancelAppointmentController {

    public record CancelBody(@Size(max = 500) String reason) {}

    private final AppointmentRepository repo;
    private final VisitRepository visits;
    private final ApplicationEventPublisher events;

    public CancelAppointmentController(
            AppointmentRepository repo,
            VisitRepository visits,
            ApplicationEventPublisher events
    ) {
        this.repo = repo;
        this.visits = visits;
        this.events = events;
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN', 'DOCTOR')")
    @Transactional
    public AppointmentResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelBody body) {
        Appointment a = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Appointment not found: " + id));
        a.cancel(body.reason());
        a.pullDomainEvents().forEach(events::publishEvent);

        // Per HMS comments: when an appointment is cancelled, also cancel the linked visit
        // (otherwise the cashier still sees the request).
        Visit visit = visits.findById(a.getVisitId()).orElse(null);
        if (visit != null && !visit.getStatus().isTerminal()) {
            visit.cancel(body.reason() != null ? body.reason() : "Appointment cancelled");
            visit.pullDomainEvents().forEach(events::publishEvent);
        }

        return AppointmentResponse.from(a);
    }
}
