package com.albudoor.hms.emergency.setdischargenote;

import com.albudoor.hms.emergency.api.CaseResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController("emergencySetDischargeNoteController")
@RequestMapping("/api/emergency/cases")
public class SetDischargeNoteController {
    private final SetDischargeNoteHandler handler;
    public SetDischargeNoteController(SetDischargeNoteHandler handler) { this.handler = handler; }

    @PostMapping("/{id}/discharge-note")
    @PreAuthorize("hasAnyRole('EMERGENCY_STAFF', 'NURSE', 'DOCTOR', 'ADMIN')")
    public CaseResponse set(@PathVariable UUID id, @Valid @RequestBody SetDischargeNoteCommand cmd) {
        return CaseResponse.from(handler.handle(id, cmd));
    }
}
