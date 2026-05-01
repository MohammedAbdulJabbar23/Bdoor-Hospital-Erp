package com.albudoor.hms.visitmanagement.transitionvisit;

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
public class TransitionVisitController {

    private final TransitionVisitHandler handler;

    public TransitionVisitController(TransitionVisitHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("isAuthenticated()")
    public VisitResponse transition(
            @PathVariable UUID id,
            @Valid @RequestBody TransitionVisitCommand cmd
    ) {
        return VisitResponse.from(handler.handle(id, cmd));
    }
}
