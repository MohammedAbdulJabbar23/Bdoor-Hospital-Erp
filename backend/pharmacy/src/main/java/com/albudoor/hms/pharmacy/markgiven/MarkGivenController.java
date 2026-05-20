package com.albudoor.hms.pharmacy.markgiven;

import com.albudoor.hms.pharmacy.api.DispenseResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dispenses")
@PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
public class MarkGivenController {

    private final MarkGivenHandler handler;

    public MarkGivenController(MarkGivenHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/mark-given")
    public DispenseResponse markGiven(@PathVariable UUID id) {
        return DispenseResponse.from(handler.handle(id));
    }
}
