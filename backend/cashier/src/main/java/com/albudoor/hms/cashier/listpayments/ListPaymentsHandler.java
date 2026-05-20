package com.albudoor.hms.cashier.listpayments;

import com.albudoor.hms.cashier.domain.Payment;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ListPaymentsHandler {

    private final PaymentRepository repo;

    public ListPaymentsHandler(PaymentRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Page<Payment> search(PaymentStatus status, PaymentStage stage, int page, int size) {
        return repo.search(status, stage, PageRequest.of(page, Math.min(size, 100)));
    }

    @Transactional(readOnly = true)
    public Payment byId(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Payment not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Payment> byVisit(UUID visitId) {
        return repo.findAllByVisitIdOrderByCreatedAtDesc(visitId);
    }

    @Transactional(readOnly = true)
    public List<Payment> byPatient(UUID patientId) {
        return repo.findAllByPatientIdOrderByCreatedAtDesc(patientId);
    }
}
