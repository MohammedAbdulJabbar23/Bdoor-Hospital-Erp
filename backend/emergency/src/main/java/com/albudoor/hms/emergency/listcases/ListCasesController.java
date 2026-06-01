package com.albudoor.hms.emergency.listcases;

import com.albudoor.hms.emergency.api.CaseResponse;
import com.albudoor.hms.emergency.domain.EmergencyCaseStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("emergencyListCasesController")
@RequestMapping("/api/emergency/cases")
@PreAuthorize("isAuthenticated()")
public class ListCasesController {

    private final ListCasesHandler handler;

    public ListCasesController(ListCasesHandler handler) {
        this.handler = handler;
    }

    @GetMapping
    public List<CaseResponse> list(
            @RequestParam(value = "status", required = false) EmergencyCaseStatus status) {
        return handler.list(status);
    }
}
