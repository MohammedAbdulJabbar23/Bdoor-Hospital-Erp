package com.albudoor.hms.doctorappointment.bookappointment;

import com.albudoor.hms.doctorappointment.domain.AppointmentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record BookAppointmentCommand(
        @NotNull UUID doctorId,
        @NotNull UUID patientId,
        /** Required for {@link AppointmentType#BOOKED}; ignored for {@code WALKIN}. */
        LocalDateTime scheduledFor,
        @NotNull AppointmentType type,
        @Size(max = 500) String notes
) {}
