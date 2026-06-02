package com.albudoor.hms.emergency.orderworkup;

import com.albudoor.hms.emergency.api.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController("emergencyOrderWorkupController")
@RequestMapping("/api/emergency/cases")
public class OrderWorkupController {
    private final OrderWorkupHandler handler;
    public OrderWorkupController(OrderWorkupHandler handler) { this.handler = handler; }

    @PostMapping("/{id}/orders")
    @PreAuthorize("hasAnyRole('EMERGENCY_STAFF', 'NURSE', 'DOCTOR', 'ADMIN')")
    public OrderResponse order(@PathVariable UUID id, @Valid @RequestBody OrderWorkupCommand cmd) {
        return OrderResponse.from(handler.handle(id, cmd));
    }
}
