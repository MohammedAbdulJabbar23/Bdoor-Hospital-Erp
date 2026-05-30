package com.albudoor.hms.premature.upsertform;

import com.albudoor.hms.premature.api.PrematureFormResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/premature/admissions")
public class UpsertFormController {

    private final UpsertFormHandler handler;

    public UpsertFormController(UpsertFormHandler handler) {
        this.handler = handler;
    }

    @PutMapping("/{id}/form")
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'DOCTOR', 'ADMIN')")
    public PrematureFormResponse upsert(@PathVariable UUID id, @Valid @RequestBody UpsertFormCommand cmd) {
        return PrematureFormResponse.from(handler.handle(id, cmd));
    }
}
