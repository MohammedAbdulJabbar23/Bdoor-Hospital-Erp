package com.albudoor.hms.premature.listorders;

import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.api.OrderResponse;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/premature/admissions")
public class ListOrdersController {
    private final PrematureAdmissionRepository admissions;
    private final VisitRepository visits;
    public ListOrdersController(PrematureAdmissionRepository admissions, VisitRepository visits) {
        this.admissions = admissions; this.visits = visits;
    }

    @GetMapping("/{id}/orders")
    @PreAuthorize("isAuthenticated()")
    public List<OrderResponse> list(@PathVariable UUID id) {
        PrematureAdmission a = admissions.findById(id)
                .orElseThrow(() -> new NotFoundException("Admission not found: " + id));
        return visits.findAllByParentVisitIdOrderByStartedAtDesc(a.getVisitId())
                .stream().map(OrderResponse::from).toList();
    }
}
