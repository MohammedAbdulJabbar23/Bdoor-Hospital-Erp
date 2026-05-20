package com.albudoor.hms.pharmacy.canceldispense;

import com.albudoor.hms.pharmacy.api.DispenseResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dispenses")
@PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
public class CancelDispenseController {

    private final CancelDispenseHandler handler;

    public CancelDispenseController(CancelDispenseHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/cancel")
    public DispenseResponse cancel(@PathVariable UUID id, @RequestBody @Valid CancelDispenseCommand cmd) {
        return DispenseResponse.from(handler.handle(id, cmd));
    }
}
