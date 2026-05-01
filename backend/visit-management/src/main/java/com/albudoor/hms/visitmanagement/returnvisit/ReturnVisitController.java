package com.albudoor.hms.visitmanagement.returnvisit;

import com.albudoor.hms.visitmanagement.api.VisitResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/visits")
public class ReturnVisitController {

    private final ReturnVisitHandler handler;

    public ReturnVisitController(ReturnVisitHandler handler) {
        this.handler = handler;
    }

    public record ReturnResponse(VisitResponse parent, VisitResponse child) {}

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('LAB_STAFF', 'RADIOLOGY_STAFF', 'ECO_STAFF', " +
            "'EMERGENCY_STAFF', 'PREMATURE_STAFF', 'DOCTOR', 'ADMIN')")
    public ReturnResponse returnToParent(
            @PathVariable UUID id,
            @Valid @RequestBody ReturnVisitCommand cmd
    ) {
        ReturnVisitHandler.ReturnResult r = handler.handle(id, cmd);
        return new ReturnResponse(VisitResponse.from(r.parent()), VisitResponse.from(r.child()));
    }
}
