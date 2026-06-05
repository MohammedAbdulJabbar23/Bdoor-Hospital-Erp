package com.albudoor.hms.pharmacy.summary;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pharmacy-queue KPI summary. Restricted to PHARMACIST/ADMIN like the dispense actions; the literal
 * {@code /summary} path is more specific than the listing's {@code /{id}} mapping, so it never
 * collides with the by-id lookup.
 */
@RestController
@RequestMapping("/api/dispenses")
@PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
public class DispenseSummaryController {

    private final DispenseSummaryService service;

    public DispenseSummaryController(DispenseSummaryService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public DispenseSummaryResponse summary() {
        return service.summary();
    }
}
