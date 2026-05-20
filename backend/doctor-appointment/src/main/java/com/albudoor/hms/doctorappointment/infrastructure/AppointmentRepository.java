package com.albudoor.hms.doctorappointment.infrastructure;

import com.albudoor.hms.doctorappointment.domain.Appointment;
import com.albudoor.hms.doctorappointment.domain.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    /** First non-cancelled appointment for the visit, if any. */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.visitId = :visitId
              AND a.status <> com.albudoor.hms.doctorappointment.domain.AppointmentStatus.CANCELLED
            """)
    Optional<Appointment> findActiveByVisitId(@Param("visitId") UUID visitId);

    List<Appointment> findAllByDoctorIdAndScheduledDateOrderByScheduledForAsc(
            UUID doctorId, LocalDate date);

    @Query("""
            SELECT a FROM Appointment a
            WHERE a.doctorId = :doctorId
              AND a.scheduledDate = :date
              AND a.status <> :excluded
            ORDER BY a.scheduledFor
            """)
    List<Appointment> findActiveByDoctorAndDate(
            @Param("doctorId") UUID doctorId,
            @Param("date") LocalDate date,
            @Param("excluded") AppointmentStatus excluded
    );

    boolean existsByDoctorIdAndScheduledForAndStatusNot(
            UUID doctorId, LocalDateTime scheduledFor, AppointmentStatus status);

    List<Appointment> findAllByPatientIdOrderByScheduledForDesc(UUID patientId);
}
