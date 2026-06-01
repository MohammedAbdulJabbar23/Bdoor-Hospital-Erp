package com.albudoor.hms.emergency.updatebed;

import com.albudoor.hms.emergency.api.BedResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController("emergencyUpdateBedController")
@RequestMapping("/api/emergency/beds")
public class UpdateBedController {

    private final UpdateBedHandler handler;

    public UpdateBedController(UpdateBedHandler handler) {
        this.handler = handler;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMERGENCY_STAFF')")
    public BedResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateBedCommand cmd) {
        return BedResponse.from(handler.handle(id, cmd));
    }
}
