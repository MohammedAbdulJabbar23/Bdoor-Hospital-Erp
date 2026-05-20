package com.albudoor.hms.cashier.listpayments;

import com.albudoor.hms.cashier.api.PaymentResponse;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@PreAuthorize("isAuthenticated()")
public class ListPaymentsController {

    private final ListPaymentsHandler handler;

    public ListPaymentsController(ListPaymentsHandler handler) {
        this.handler = handler;
    }

    @GetMapping
    public Page<PaymentResponse> search(
            @RequestParam(value = "status", required = false) PaymentStatus status,
            @RequestParam(value = "stage", required = false) PaymentStage stage,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return handler.search(status, stage, page, size).map(PaymentResponse::from);
    }

    @GetMapping("/{id}")
    public PaymentResponse byId(@PathVariable UUID id) {
        return PaymentResponse.from(handler.byId(id));
    }

    @GetMapping("/by-visit/{visitId}")
    public List<PaymentResponse> byVisit(@PathVariable UUID visitId) {
        return handler.byVisit(visitId).stream().map(PaymentResponse::from).toList();
    }

    @GetMapping("/by-patient/{patientId}")
    public List<PaymentResponse> byPatient(@PathVariable UUID patientId) {
        return handler.byPatient(patientId).stream().map(PaymentResponse::from).toList();
    }
}
