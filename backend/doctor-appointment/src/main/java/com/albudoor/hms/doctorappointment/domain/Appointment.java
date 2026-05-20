package com.albudoor.hms.doctorappointment.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One appointment between a {@link Doctor} and a Patient. Owns the slot reservation —
 * uniqueness on {@code (doctor_id, scheduled_for, status != CANCELLED)} is enforced via
 * application-level guard rather than DB partial index for portability.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "appointment",
        indexes = {
                @Index(name = "idx_appt_doctor_date", columnList = "doctor_id, scheduled_for"),
                @Index(name = "idx_appt_patient",     columnList = "patient_id"),
                @Index(name = "idx_appt_visit",       columnList = "visit_id"),
                @Index(name = "idx_appt_date",        columnList = "scheduled_date")
        }
)
public class Appointment extends AggregateRoot {

    @Id
    private UUID id;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "doctor_name", nullable = false, length = 200)
    private String doctorName;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "patient_mrn", nullable = false, length = 30)
    private String patientMrn;

    @Column(name = "patient_name", nullable = false, length = 300)
    private String patientName;

    /** The {@code Visit} created when the appointment was booked. */
    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "scheduled_for", nullable = false)
    private LocalDateTime scheduledFor;

    /** Denormalised for fast date-range queries without time-zone arithmetic. */
    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status;

    @Column(length = 500)
    private String notes;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static Appointment book(
            UUID doctorId, String doctorName,
            UUID patientId, String patientMrn, String patientName,
            UUID visitId,
            LocalDateTime scheduledFor,
            int durationMinutes,
            AppointmentType type,
            String notes
    ) {
        if (scheduledFor == null) {
            throw new DomainException("SCHEDULED_FOR_REQUIRED", "Scheduled time is required");
        }
        if (durationMinutes <= 0) {
            throw new DomainException("INVALID_DURATION", "Duration must be positive");
        }

        Appointment a = new Appointment();
        a.id = UUID.randomUUID();
        a.doctorId = doctorId;
        a.doctorName = doctorName;
        a.patientId = patientId;
        a.patientMrn = patientMrn;
        a.patientName = patientName;
        a.visitId = visitId;
        a.scheduledFor = scheduledFor;
        a.scheduledDate = scheduledFor.toLocalDate();
        a.durationMinutes = durationMinutes;
        a.type = type;
        a.status = AppointmentStatus.BOOKED;
        a.notes = notes;
        a.registerEvent(AppointmentBookedEvent.of(a));
        return a;
    }

    public void cancel(String reason) {
        if (this.status == AppointmentStatus.COMPLETED) {
            throw new DomainException("APPT_COMPLETED", "Cannot cancel a completed appointment");
        }
        if (this.status == AppointmentStatus.CANCELLED) return;
        this.status = AppointmentStatus.CANCELLED;
        this.cancellationReason = reason;
        registerEvent(AppointmentCancelledEvent.of(this));
    }

    public void checkIn() {
        if (this.status != AppointmentStatus.BOOKED) {
            throw new DomainException("APPT_NOT_BOOKED",
                    "Cannot check in an appointment in status " + this.status);
        }
        this.status = AppointmentStatus.CHECKED_IN;
        this.checkedInAt = Instant.now();
        registerEvent(AppointmentCheckedInEvent.of(this));
    }

    public void complete() {
        if (this.status != AppointmentStatus.CHECKED_IN && this.status != AppointmentStatus.BOOKED) {
            throw new DomainException("APPT_INVALID_COMPLETE",
                    "Cannot complete an appointment in status " + this.status);
        }
        this.status = AppointmentStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void markNoShow() {
        if (this.status != AppointmentStatus.BOOKED) {
            throw new DomainException("APPT_NOT_BOOKED",
                    "Cannot mark no-show on appointment in status " + this.status);
        }
        this.status = AppointmentStatus.NO_SHOW;
    }
}
