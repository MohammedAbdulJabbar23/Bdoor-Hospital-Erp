package com.albudoor.hms.cashier.domain;

import com.albudoor.hms.platform.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PaymentCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID paymentId,
        UUID visitId,
        PaymentStage stage,
        PaymentStatus initialStatus,
        boolean vipBypass
) implements DomainEvent {
    public static PaymentCreatedEvent of(Payment p) {
        return new PaymentCreatedEvent(UUID.randomUUID(), Instant.now(),
                p.getId(), p.getVisitId(), p.getStage(), p.getStatus(), p.isVipBypass());
    }
}
