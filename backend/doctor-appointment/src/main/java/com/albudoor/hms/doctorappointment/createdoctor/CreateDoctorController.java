package com.albudoor.hms.doctorappointment.createdoctor;

import com.albudoor.hms.doctorappointment.api.DoctorResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doctors")
public class CreateDoctorController {

    private final CreateDoctorHandler handler;

    public CreateDoctorController(CreateDoctorHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DoctorResponse> create(@Valid @RequestBody CreateDoctorCommand cmd) {
        return ResponseEntity.status(HttpStatus.CREATED).body(DoctorResponse.from(handler.handle(cmd)));
    }
}
