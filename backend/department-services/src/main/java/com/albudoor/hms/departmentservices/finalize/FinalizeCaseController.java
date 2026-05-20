package com.albudoor.hms.departmentservices.finalize;

import com.albudoor.hms.departmentservices.api.DepartmentCaseResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dept-cases")
public class FinalizeCaseController {

    private final FinalizeCaseHandler handler;

    public FinalizeCaseController(FinalizeCaseHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasAnyRole('LAB_STAFF', 'RADIOLOGY_STAFF', 'ECO_STAFF', 'ADMIN')")
    public DepartmentCaseResponse finalize(@PathVariable UUID id) {
        return DepartmentCaseResponse.from(handler.handle(id));
    }
}
