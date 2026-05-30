package com.albudoor.hms.premature.getcase;

import com.albudoor.hms.premature.api.PrematureCaseResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/premature/admissions")
@PreAuthorize("isAuthenticated()")
public class GetCaseController {

    private final GetCaseHandler handler;

    public GetCaseController(GetCaseHandler handler) {
        this.handler = handler;
    }

    @GetMapping("/{id}/case")
    public PrematureCaseResponse getCase(@PathVariable UUID id) {
        return handler.handle(id);
    }
}
