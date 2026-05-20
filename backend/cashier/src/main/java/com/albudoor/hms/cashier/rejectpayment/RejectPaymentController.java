package com.albudoor.hms.cashier.rejectpayment;

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
public class RejectPaymentController {

    private final RejectPaymentHandler handler;

    public RejectPaymentController(RejectPaymentHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('CASHIER', 'ADMIN')")
    public PaymentResponse reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectPaymentCommand cmd
    ) {
        return PaymentResponse.from(handler.handle(id, cmd));
    }
}
