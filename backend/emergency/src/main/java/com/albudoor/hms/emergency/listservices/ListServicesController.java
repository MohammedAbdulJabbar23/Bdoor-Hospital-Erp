package com.albudoor.hms.emergency.listservices;

import com.albudoor.hms.emergency.api.EmergencyServiceResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("emergencyListServicesController")
@RequestMapping("/api/emergency/services")
@PreAuthorize("isAuthenticated()")
public class ListServicesController {
    private final ListServicesHandler handler;
    public ListServicesController(ListServicesHandler handler) { this.handler = handler; }
    @GetMapping public List<EmergencyServiceResponse> list() { return handler.list(); }
}
