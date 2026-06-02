package com.albudoor.hms.emergency.listorders;

import com.albudoor.hms.emergency.api.OrderResponse;
import com.albudoor.hms.emergency.domain.EmergencyCase;
import com.albudoor.hms.emergency.infrastructure.EmergencyCaseRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController("emergencyListOrdersController")
@RequestMapping("/api/emergency/cases")
public class ListOrdersController {
    private final EmergencyCaseRepository cases;
    private final VisitRepository visits;
    public ListOrdersController(EmergencyCaseRepository cases, VisitRepository visits) {
        this.cases = cases; this.visits = visits;
    }

    @GetMapping("/{id}/orders")
    @PreAuthorize("isAuthenticated()")
    public List<OrderResponse> list(@PathVariable UUID id) {
        EmergencyCase c = cases.findById(id)
                .orElseThrow(() -> new NotFoundException("Case not found: " + id));
        return visits.findAllByParentVisitIdOrderByStartedAtDesc(c.getVisitId())
                .stream().map(OrderResponse::from).toList();
    }
}
