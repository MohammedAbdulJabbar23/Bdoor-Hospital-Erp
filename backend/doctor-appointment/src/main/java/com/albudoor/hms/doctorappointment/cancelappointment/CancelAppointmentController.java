package com.albudoor.hms.doctorappointment.cancelappointment;

import com.albudoor.hms.doctorappointment.api.AppointmentResponse;
import com.albudoor.hms.doctorappointment.domain.Appointment;
import com.albudoor.hms.doctorappointment.infrastructure.AppointmentRepository;
import com.albudoor.hms.platform.exception.ConflictException;
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

        // Cancel-after-paid guard: once the consult payment is APPROVED the visit has moved
        // past AWAITING_PAYMENT (IN_PROGRESS or beyond). Silently voiding a paid, in-progress
        // visit would lose the payment record and the in-flight clinical work, so refuse.
        Visit visit = visits.findById(a.getVisitId()).orElse(null);
        if (visit != null && isPaidOrInProgress(visit.getStatus())) {
            throw new ConflictException("APPOINTMENT_PAID_IN_PROGRESS",
                    "Cannot cancel: the consult payment is approved and the visit is "
                            + visit.getStatus() + ". Complete or cancel the visit instead.");
        }

        a.cancel(body.reason());
        a.pullDomainEvents().forEach(events::publishEvent);

        // Per HMS comments: when an appointment is cancelled, also cancel the linked visit
        // (otherwise the cashier still sees the request).
        if (visit != null && !visit.getStatus().isTerminal()) {
            visit.cancel(body.reason() != null ? body.reason() : "Appointment cancelled");
            visit.pullDomainEvents().forEach(events::publishEvent);
        }

        return AppointmentResponse.from(a);
    }

    /** Any visit state from IN_PROGRESS onward implies the consult payment was approved. */
    private static boolean isPaidOrInProgress(VisitStatus status) {
        return status == VisitStatus.IN_PROGRESS
                || status == VisitStatus.AWAITING_RESULTS
                || status == VisitStatus.TREATMENT_FINISHED
                || status == VisitStatus.AWAITING_FINAL_PAYMENT
                || status == VisitStatus.OUTSTANDING_BALANCE;
    }
}
