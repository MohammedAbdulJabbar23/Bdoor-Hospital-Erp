package com.albudoor.hms.doctorappointment.setschedule;

import com.albudoor.hms.doctorappointment.api.DoctorResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/doctors")
public class SetScheduleController {

    private final SetScheduleHandler handler;

    public SetScheduleController(SetScheduleHandler handler) {
        this.handler = handler;
    }

    @PutMapping("/{id}/schedule")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public DoctorResponse setSchedule(@PathVariable UUID id, @Valid @RequestBody SetScheduleCommand cmd) {
        return DoctorResponse.from(handler.handle(id, cmd));
    }
}
