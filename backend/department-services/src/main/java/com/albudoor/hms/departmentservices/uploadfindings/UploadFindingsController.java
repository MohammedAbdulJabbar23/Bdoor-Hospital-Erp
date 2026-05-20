package com.albudoor.hms.departmentservices.uploadfindings;

import com.albudoor.hms.departmentservices.api.DepartmentCaseResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dept-cases")
public class UploadFindingsController {

    private final UploadFindingsHandler handler;

    public UploadFindingsController(UploadFindingsHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/findings")
    @PreAuthorize("hasAnyRole('LAB_STAFF', 'RADIOLOGY_STAFF', 'ECO_STAFF', 'ADMIN')")
    public DepartmentCaseResponse upload(
            @PathVariable UUID id,
            @Valid @RequestBody UploadFindingsCommand cmd
    ) {
        return DepartmentCaseResponse.from(handler.handle(id, cmd));
    }
}
