package com.albudoor.hms.doctorappointment.setschedule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record SetScheduleCommand(
        @NotEmpty @Valid List<Block> blocks
) {
    public record Block(
            @NotNull DayOfWeek dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @Positive int slotMinutes
    ) {}
}
