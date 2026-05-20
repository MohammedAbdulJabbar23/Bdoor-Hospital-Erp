package com.albudoor.hms.doctorappointment.domain;

public enum AppointmentStatus {
    BOOKED,        // Slot reserved, patient not yet arrived
    CHECKED_IN,    // Patient arrived; visit + payment in flight
    COMPLETED,     // Doctor finished the consultation
    CANCELLED,     // Cancelled by reception or patient
    NO_SHOW        // Patient never arrived; auto-marked or admin-marked
}
