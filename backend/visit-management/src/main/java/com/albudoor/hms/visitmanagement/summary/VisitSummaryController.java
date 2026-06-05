package com.albudoor.hms.visitmanagement.summary;

import com.albudoor.hms.visitmanagement.domain.VisitType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Visit-queue KPI summary. Same auth as the queue listing ({@code isAuthenticated()}); the literal
 * {@code /summary} path is more specific than the queue's {@code /{id}} mapping, so it never
 * collides with the by-id lookup.
 */
@RestController
@RequestMapping("/api/visits")
@PreAuthorize("isAuthenticated()")
public class VisitSummaryController {

    private final VisitSummaryService service;

    public VisitSummaryController(VisitSummaryService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public VisitSummaryResponse summary(
            @RequestParam(value = "type", required = false) VisitType type
    ) {
        return service.summary(type);
    }
}
