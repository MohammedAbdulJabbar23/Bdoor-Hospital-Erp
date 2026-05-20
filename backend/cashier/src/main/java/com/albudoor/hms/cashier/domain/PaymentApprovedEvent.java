package com.albudoor.hms.cashier.domain;

import com.albudoor.hms.platform.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PaymentApprovedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID paymentId,
        UUID visitId,
        PaymentStage stage,
        boolean vipBypass
) implements DomainEvent {
    public static PaymentApprovedEvent of(Payment p) {
        return new PaymentApprovedEvent(UUID.randomUUID(), Instant.now(),
                p.getId(), p.getVisitId(), p.getStage(), p.isVipBypass());
    }
}
