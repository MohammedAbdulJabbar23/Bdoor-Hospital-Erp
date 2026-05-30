package com.albudoor.hms.premature.recordtour;

import com.albudoor.hms.premature.api.PrematureTourResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/premature/admissions")
public class RecordTourController {

    private final RecordTourHandler handler;

    public RecordTourController(RecordTourHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/tours")
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'DOCTOR', 'NURSE', 'ADMIN')")
    public ResponseEntity<PrematureTourResponse> record(@PathVariable UUID id, @Valid @RequestBody RecordTourCommand cmd) {
        return ResponseEntity.status(HttpStatus.CREATED).body(PrematureTourResponse.from(handler.handle(id, cmd)));
    }
}
