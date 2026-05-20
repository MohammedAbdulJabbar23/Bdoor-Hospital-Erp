package com.albudoor.hms.cashier.api;

import com.albudoor.hms.cashier.domain.Payment;
import com.albudoor.hms.cashier.domain.PaymentLineItem;
import com.albudoor.hms.cashier.domain.PaymentMethod;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.visitmanagement.domain.VisitType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String paymentDisplayId,
        UUID visitId,
        String visitDisplayId,
        VisitType visitType,
        UUID patientId,
        String patientMrn,
        String patientName,
        boolean patientWasVip,
        PaymentStage stage,
        PaymentStatus status,
        boolean vipBypass,
        BigDecimal totalDue,
        String currency,
        PaymentMethod paymentMethod,
        UUID cashierUserId,
        Instant decidedAt,
        String rejectionReason,
        Instant createdAt,
        List<Line> lines
) {
    public record Line(
            UUID serviceItemId,
            String code,
            String name,
            BigDecimal unitFee,
            int quantity,
            BigDecimal lineTotal
    ) {}

    public static PaymentResponse from(Payment p) {
        List<Line> lines = p.getLineItems().stream()
                .map(PaymentResponse::lineOf)
                .toList();
        return new PaymentResponse(
                p.getId(), p.getPaymentDisplayId(),
                p.getVisitId(), p.getVisitDisplayId(), p.getVisitType(),
                p.getPatientId(), p.getPatientMrn(), p.getPatientName(), p.isPatientWasVip(),
                p.getStage(), p.getStatus(), p.isVipBypass(),
                p.getTotalDue(), p.getCurrency(),
                p.getPaymentMethod(), p.getCashierUserId(), p.getDecidedAt(),
                p.getRejectionReason(),
                p.getCreatedAt(),
                lines
        );
    }

    private static Line lineOf(PaymentLineItem li) {
        return new Line(li.getServiceItemId(), li.getServiceCode(), li.getServiceName(),
                li.getUnitFee(), li.getQuantity(), li.getLineTotal());
    }
}
