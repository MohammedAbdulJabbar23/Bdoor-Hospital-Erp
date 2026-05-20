package com.albudoor.hms.doctorappointment.bookappointment;

import com.albudoor.hms.doctorappointment.api.AppointmentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
public class BookAppointmentController {

    private final BookAppointmentHandler handler;

    public BookAppointmentController(BookAppointmentHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<AppointmentResponse> book(@Valid @RequestBody BookAppointmentCommand cmd) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AppointmentResponse.from(handler.handle(cmd)));
    }
}
