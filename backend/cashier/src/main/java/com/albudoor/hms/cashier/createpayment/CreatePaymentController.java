package com.albudoor.hms.cashier.createpayment;

import com.albudoor.hms.cashier.api.PaymentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class CreatePaymentController {

    private final CreatePaymentHandler handler;

    public CreatePaymentController(CreatePaymentHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', " +
            "'EMERGENCY_STAFF', 'PREMATURE_STAFF', 'LAB_STAFF', " +
            "'RADIOLOGY_STAFF', 'ECO_STAFF', 'PHARMACIST', 'CASHIER', 'ADMIN')")
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentCommand cmd) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PaymentResponse.from(handler.handle(cmd)));
    }
}
