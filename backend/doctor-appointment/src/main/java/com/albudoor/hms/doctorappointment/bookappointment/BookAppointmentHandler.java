package com.albudoor.hms.doctorappointment.bookappointment;

import com.albudoor.hms.doctorappointment.api.SlotComputationService;
import com.albudoor.hms.doctorappointment.domain.Appointment;
import com.albudoor.hms.doctorappointment.domain.AppointmentSlot;
import com.albudoor.hms.doctorappointment.domain.AppointmentStatus;
import com.albudoor.hms.doctorappointment.domain.AppointmentType;
import com.albudoor.hms.doctorappointment.domain.Doctor;
import com.albudoor.hms.doctorappointment.infrastructure.AppointmentRepository;
import com.albudoor.hms.doctorappointment.infrastructure.DoctorRepository;
import com.albudoor.hms.patientregistry.domain.Patient;
import com.albudoor.hms.patientregistry.infrastructure.PatientRepository;
import com.albudoor.hms.platform.exception.ConflictException;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitOrigin;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import com.albudoor.hms.visitmanagement.infrastructure.VisitIdGenerator;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
public class BookAppointmentHandler {

    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final AppointmentRepository appointments;
    private final VisitRepository visits;
    private final VisitIdGenerator visitIdGenerator;
    private final SlotComputationService slots;
    private final ApplicationEventPublisher events;

    public BookAppointmentHandler(
            DoctorRepository doctors,
            PatientRepository patients,
            AppointmentRepository appointments,
            VisitRepository visits,
            VisitIdGenerator visitIdGenerator,
            SlotComputationService slots,
            ApplicationEventPublisher events
    ) {
        this.doctors = doctors;
        this.patients = patients;
        this.appointments = appointments;
        this.visits = visits;
        this.visitIdGenerator = visitIdGenerator;
        this.slots = slots;
        this.events = events;
    }

    @Transactional
    public Appointment handle(BookAppointmentCommand cmd) {
        Doctor doctor = doctors.findById(cmd.doctorId())
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + cmd.doctorId()));
        if (!doctor.isActive()) {
            throw new DomainException("DOCTOR_INACTIVE", "Doctor is not active: " + doctor.getFullName());
        }
        Patient patient = patients.findById(cmd.patientId())
                .orElseThrow(() -> new NotFoundException("Patient not found: " + cmd.patientId()));

        // Resolve the time slot
        LocalDateTime scheduledFor;
        int durationMinutes;

        if (cmd.type() == AppointmentType.BOOKED) {
            if (cmd.scheduledFor() == null) {
                throw new DomainException("SCHEDULED_FOR_REQUIRED",
                        "scheduledFor is required for BOOKED appointments");
            }
            scheduledFor = cmd.scheduledFor();
            durationMinutes = resolveSlotDuration(doctor, scheduledFor)
                    .orElseThrow(() -> new DomainException("SLOT_NOT_AVAILABLE",
                            "No slot at " + scheduledFor + " for this doctor"));
            if (appointments.existsByDoctorIdAndScheduledForAndStatusNot(
                    doctor.getId(), scheduledFor, AppointmentStatus.CANCELLED)) {
                throw new ConflictException("SLOT_TAKEN",
                        "Slot already booked at " + scheduledFor);
            }
        } else {
            // Walk-in: fill the next free slot today, or queue at end of day if all full.
            LocalDate today = LocalDate.now();
            List<Appointment> sameDay = appointments.findActiveByDoctorAndDate(
                    doctor.getId(), today, AppointmentStatus.CANCELLED);
            List<AppointmentSlot> slotList = slots.compute(doctor, today, sameDay);

            AppointmentSlot pick = slotList.stream()
                    .filter(s -> s.available() && !s.startsAt().isBefore(LocalDateTime.now()))
                    .min(Comparator.comparing(AppointmentSlot::startsAt))
                    .orElse(null);

            if (pick != null) {
                scheduledFor = pick.startsAt();
                durationMinutes = pick.durationMinutes();
            } else {
                // No free slot left today: queue at the end of the doctor's last block,
                // using the last block's slot duration as a default.
                LocalTime endOfDay = doctor.getWeeklyHours().stream()
                        .filter(w -> w.getDayOfWeek() == today.getDayOfWeek())
                        .map(w -> w.getEndTime())
                        .max(Comparator.naturalOrder())
                        .orElse(LocalTime.of(20, 0));
                int defaultSlot = doctor.getWeeklyHours().stream()
                        .filter(w -> w.getDayOfWeek() == today.getDayOfWeek())
                        .map(w -> w.getSlotMinutes())
                        .findFirst()
                        .orElse(15);
                scheduledFor = LocalDateTime.of(today, endOfDay);
                durationMinutes = defaultSlot;
            }
        }

        // Create the visit first; the visit id is the patient's lifecycle anchor.
        Visit visit = Visit.createDirect(
                visitIdGenerator.next(),
                patient.getId(), patient.getMrn(), patient.getFullName(),
                VisitType.DOCTOR_APPOINTMENT,
                VisitOrigin.DIRECT_RETURNING,
                doctor.getId()
        );
        visits.save(visit);
        visit.pullDomainEvents().forEach(events::publishEvent);

        Appointment appt = Appointment.book(
                doctor.getId(), doctor.getFullName(),
                patient.getId(), patient.getMrn(), patient.getFullName(),
                visit.getId(),
                scheduledFor, durationMinutes,
                cmd.type(), cmd.notes()
        );
        Appointment saved = appointments.save(appt);
        appt.pullDomainEvents().forEach(events::publishEvent);
        return saved;
    }

    private static java.util.Optional<Integer> resolveSlotDuration(Doctor doctor, LocalDateTime scheduledFor) {
        return doctor.getWeeklyHours().stream()
                .filter(w -> w.getDayOfWeek() == scheduledFor.getDayOfWeek())
                .filter(w -> isInside(w.getStartTime(), w.getEndTime(),
                        scheduledFor.toLocalTime(), Duration.ofMinutes(w.getSlotMinutes())))
                .map(w -> w.getSlotMinutes())
                .findFirst();
    }

    private static boolean isInside(LocalTime blockStart, LocalTime blockEnd, LocalTime t, Duration slot) {
        return !t.isBefore(blockStart) && !t.plus(slot).isAfter(blockEnd);
    }
}
