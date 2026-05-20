package com.albudoor.hms.departmentservices.opencase;

import com.albudoor.hms.departmentservices.api.DepartmentCaseResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dept-cases")
public class OpenCaseController {

    private final OpenCaseHandler handler;

    public OpenCaseController(OpenCaseHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/open")
    @PreAuthorize("hasAnyRole('LAB_STAFF', 'RADIOLOGY_STAFF', 'ECO_STAFF', 'ADMIN')")
    public ResponseEntity<DepartmentCaseResponse> open(@Valid @RequestBody OpenCaseCommand cmd) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DepartmentCaseResponse.from(handler.handle(cmd)));
    }
}
