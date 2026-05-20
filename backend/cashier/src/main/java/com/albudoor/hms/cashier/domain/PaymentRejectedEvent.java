package com.albudoor.hms.cashier.domain;

import com.albudoor.hms.platform.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PaymentRejectedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID paymentId,
        UUID visitId,
        PaymentStage stage,
        String reason
) implements DomainEvent {
    public static PaymentRejectedEvent of(Payment p) {
        return new PaymentRejectedEvent(UUID.randomUUID(), Instant.now(),
                p.getId(), p.getVisitId(), p.getStage(), p.getRejectionReason());
    }
}
