package com.albudoor.hms.premature.setdischargenote;

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
public class SetDischargeNoteController {
    private final SetDischargeNoteHandler handler;
    public SetDischargeNoteController(SetDischargeNoteHandler handler) { this.handler = handler; }

    @PostMapping("/{id}/discharge-note")
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'NURSE', 'DOCTOR', 'ADMIN')")
    public AdmissionResponse set(@PathVariable UUID id, @Valid @RequestBody SetDischargeNoteCommand cmd) {
        return AdmissionResponse.from(handler.handle(id, cmd));
    }
}
