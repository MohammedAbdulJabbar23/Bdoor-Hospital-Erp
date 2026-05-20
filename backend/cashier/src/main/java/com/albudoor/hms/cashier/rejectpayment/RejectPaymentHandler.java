package com.albudoor.hms.cashier.rejectpayment;

import com.albudoor.hms.cashier.domain.Payment;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RejectPaymentHandler {

    private final PaymentRepository repo;
    private final ApplicationEventPublisher events;

    public RejectPaymentHandler(PaymentRepository repo, ApplicationEventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    @Transactional
    public Payment handle(UUID id, RejectPaymentCommand cmd) {
        Payment payment = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Payment not found: " + id));

        UUID cashierId = currentUserId();
        payment.reject(cmd.reason(), cashierId);
        payment.pullDomainEvents().forEach(events::publishEvent);
        return payment;
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HmsUserPrincipal p) {
            return p.userId();
        }
        return null;
    }
}
