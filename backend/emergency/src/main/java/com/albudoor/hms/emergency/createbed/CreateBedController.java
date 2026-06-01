package com.albudoor.hms.emergency.createbed;

import com.albudoor.hms.emergency.api.BedResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("emergencyCreateBedController")
@RequestMapping("/api/emergency/beds")
public class CreateBedController {

    private final CreateBedHandler handler;

    public CreateBedController(CreateBedHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMERGENCY_STAFF')")
    public ResponseEntity<BedResponse> create(@Valid @RequestBody CreateBedCommand cmd) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BedResponse.from(handler.handle(cmd)));
    }
}
