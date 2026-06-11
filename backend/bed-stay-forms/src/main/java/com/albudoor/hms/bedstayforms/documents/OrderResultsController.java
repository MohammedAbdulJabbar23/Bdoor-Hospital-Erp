package com.albudoor.hms.bedstayforms.documents;

import com.albudoor.hms.bedstayforms.access.BedStayAccess;
import com.albudoor.hms.bedstayforms.api.OrderResultsResponse;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/bed-stays/{department}/{stayId}/orders/{visitId}/results")
public class OrderResultsController {

    private final OrderResultsHandler handler;
    private final BedStayAccess access;

    public OrderResultsController(OrderResultsHandler handler, BedStayAccess access) {
        this.handler = handler;
        this.access = access;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public OrderResultsResponse results(@PathVariable StayDepartment department,
                                        @PathVariable UUID stayId,
                                        @PathVariable UUID visitId) {
        access.checkRead(department);
        return handler.results(department, stayId, visitId);
    }
}
