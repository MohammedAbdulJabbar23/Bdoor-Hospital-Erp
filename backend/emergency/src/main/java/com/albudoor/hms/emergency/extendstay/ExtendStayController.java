package com.albudoor.hms.emergency.extendstay;

import com.albudoor.hms.emergency.api.CaseResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController("emergencyExtendStayController")
@RequestMapping("/api/emergency/cases")
public class ExtendStayController {

    private final ExtendStayHandler handler;

    public ExtendStayController(ExtendStayHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/extend-stay")
    @PreAuthorize("hasAnyRole('EMERGENCY_STAFF', 'NURSE', 'DOCTOR', 'ADMIN')")
    public CaseResponse extend(@PathVariable UUID id, @Valid @RequestBody ExtendStayCommand cmd) {
        return CaseResponse.from(handler.handle(id, cmd));
    }
}
