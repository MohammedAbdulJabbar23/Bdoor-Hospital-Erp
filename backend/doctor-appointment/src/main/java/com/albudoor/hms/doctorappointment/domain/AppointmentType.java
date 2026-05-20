package com.albudoor.hms.doctorappointment.domain;

public enum AppointmentType {
    /** Booked into a specific calendar slot. */
    BOOKED,
    /** Walk-in — slotted at the next available time or queued at end of day. */
    WALKIN
}
