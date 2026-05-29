package com.albudoor.hms.premature.extendstay;

import com.albudoor.hms.premature.api.AdmissionResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/premature/admissions")
public class ExtendStayController {

    private final ExtendStayHandler handler;

    public ExtendStayController(ExtendStayHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/extend-stay")
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'NURSE', 'DOCTOR', 'ADMIN')")
    public AdmissionResponse extend(@PathVariable UUID id, @Valid @RequestBody ExtendStayCommand cmd) {
        return AdmissionResponse.from(handler.handle(id, cmd));
    }
}
