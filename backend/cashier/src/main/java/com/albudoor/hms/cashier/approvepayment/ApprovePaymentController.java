package com.albudoor.hms.cashier.approvepayment;

import com.albudoor.hms.cashier.api.PaymentResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class ApprovePaymentController {

    private final ApprovePaymentHandler handler;

    public ApprovePaymentController(ApprovePaymentHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('CASHIER', 'ADMIN')")
    public PaymentResponse approve(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovePaymentCommand cmd
    ) {
        return PaymentResponse.from(handler.handle(id, cmd));
    }
}
