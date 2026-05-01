package com.albudoor.hms.visitmanagement.createvisit;

import com.albudoor.hms.visitmanagement.api.VisitResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visits")
public class CreateVisitController {

    private final CreateVisitHandler handler;

    public CreateVisitController(CreateVisitHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN', 'DOCTOR', 'NURSE', " +
            "'EMERGENCY_STAFF', 'PREMATURE_STAFF', 'LAB_STAFF', 'RADIOLOGY_STAFF', 'ECO_STAFF')")
    public ResponseEntity<VisitResponse> create(@Valid @RequestBody CreateVisitCommand cmd) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(VisitResponse.from(handler.handle(cmd)));
    }
}
