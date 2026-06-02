package com.albudoor.hms.cashier.summary;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Cashier reporting endpoints: live KPI summary and close-of-day reconciliation.
 * Restricted to CASHIER/ADMIN like the rest of the payment queue.
 */
@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasAnyRole('CASHIER', 'ADMIN')")
public class PaymentSummaryController {

    private final PaymentSummaryService service;

    public PaymentSummaryController(PaymentSummaryService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public PaymentSummaryResponse summary() {
        return service.summary();
    }

    @GetMapping("/reconciliation")
    public ReconciliationResponse reconciliation(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return service.reconciliation(date);
    }
}
