package com.albudoor.hms.visitmanagement.forwardvisit;

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
public class ForwardVisitController {

    private final ForwardVisitHandler handler;

    public ForwardVisitController(ForwardVisitHandler handler) {
        this.handler = handler;
    }

    public record ForwardResponse(VisitResponse parent, VisitResponse child) {}

    @PostMapping("/{id}/forward")
    @PreAuthorize("hasAnyRole('DOCTOR', 'EMERGENCY_STAFF', 'PREMATURE_STAFF', 'ADMIN')")
    public ForwardResponse forward(
            @PathVariable UUID id,
            @Valid @RequestBody ForwardVisitCommand cmd
    ) {
        ForwardVisitHandler.ForwardResult r = handler.handle(id, cmd);
        return new ForwardResponse(VisitResponse.from(r.parent()), VisitResponse.from(r.child()));
    }
}
