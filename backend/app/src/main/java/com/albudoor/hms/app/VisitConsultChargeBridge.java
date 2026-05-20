package com.albudoor.hms.app;

import com.albudoor.hms.cashier.createpayment.CreatePaymentHandler;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.doctorappointment.domain.Appointment;
import com.albudoor.hms.doctorappointment.domain.AppointmentCheckedInEvent;
import com.albudoor.hms.doctorappointment.domain.Doctor;
import com.albudoor.hms.doctorappointment.infrastructure.AppointmentRepository;
import com.albudoor.hms.doctorappointment.infrastructure.DoctorRepository;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitCreatedEvent;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Auto-creates the consult-fee payment and parks the visit at AWAITING_PAYMENT for
 * doctor-appointment visits. Two entry points:
 *
 * <ul>
 *   <li><b>Walk-in</b> — receptionist creates the visit directly with an assigned doctor.
 *       The bridge sees {@link VisitCreatedEvent} with no associated appointment and charges
 *       immediately.</li>
 *   <li><b>Booked</b> — receptionist books an appointment (which also creates a visit), then
 *       checks the patient in. The bridge sees {@link AppointmentCheckedInEvent} and charges.
 *       The earlier VisitCreatedEvent is skipped because an appointment exists for the visit.</li>
 * </ul>
 *
 * Idempotent against double-fires by checking for an existing pending/approved INITIAL
 * payment on the visit before creating one.
 */
@Component
public class VisitConsultChargeBridge {

    private static final Logger log = LoggerFactory.getLogger(VisitConsultChargeBridge.class);

    private final VisitRepository visits;
    private final DoctorRepository doctors;
    private final AppointmentRepository appointments;
    private final PaymentRepository payments;
    private final CreatePaymentHandler createPayment;
    private final ApplicationEventPublisher events;

    public VisitConsultChargeBridge(
            VisitRepository visits,
            DoctorRepository doctors,
            AppointmentRepository appointments,
            PaymentRepository payments,
            CreatePaymentHandler createPayment,
            ApplicationEventPublisher events
    ) {
        this.visits = visits;
        this.doctors = doctors;
        this.appointments = appointments;
        this.payments = payments;
        this.createPayment = createPayment;
        this.events = events;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onVisitCreated(VisitCreatedEvent event) {
        if (event.visitType() != VisitType.DOCTOR_APPOINTMENT) return;
        if (event.assignedDoctorId() == null) return;
        // If an appointment exists for this visit, the booked-flow check-in will charge.
        if (appointments.findActiveByVisitId(event.visitId()).isPresent()) return;

        chargeConsult(event.visitId(), event.assignedDoctorId());
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAppointmentCheckedIn(AppointmentCheckedInEvent event) {
        Appointment appt = appointments.findById(event.appointmentId()).orElse(null);
        if (appt == null) {
            log.warn("AppointmentCheckedIn for unknown appointment {}", event.appointmentId());
            return;
        }
        chargeConsult(appt.getVisitId(), appt.getDoctorId());
    }

    private void chargeConsult(UUID visitId, UUID doctorId) {
        if (payments.existsByVisitIdAndStageAndStatusIn(
                visitId, PaymentStage.INITIAL,
                java.util.List.of(PaymentStatus.PENDING, PaymentStatus.APPROVED))) {
            log.debug("Consult charge already exists for visit {}, skipping", visitId);
            return;
        }
        Visit visit = visits.findById(visitId).orElse(null);
        if (visit == null) {
            log.warn("Visit {} not found while charging consult", visitId);
            return;
        }
        if (visit.getStatus() != VisitStatus.CREATED) {
            log.debug("Visit {} is in {}, not CREATED — skipping consult auto-charge",
                    visitId, visit.getStatus());
            return;
        }
        Doctor doctor = doctors.findById(doctorId).orElse(null);
        if (doctor == null) {
            log.warn("Doctor {} not found while charging consult for visit {}", doctorId, visitId);
            return;
        }

        try {
            createPayment.handleConsult(
                    visitId, doctor.getFullName(), doctor.getConsultationFee(), doctor.getCurrency());
        } catch (RuntimeException ex) {
            log.warn("Could not create consult charge for visit {}: {}", visitId, ex.getMessage());
            return;
        }

        try {
            visit.transitionTo(VisitStatus.AWAITING_PAYMENT);
            visit.pullDomainEvents().forEach(events::publishEvent);
            log.info("Visit {} → AWAITING_PAYMENT (consult fee for {})",
                    visit.getVisitDisplayId(), doctor.getFullName());
        } catch (RuntimeException ex) {
            log.warn("Visit {} consult charge created but transition to AWAITING_PAYMENT failed: {}",
                    visitId, ex.getMessage());
        }
    }
}
