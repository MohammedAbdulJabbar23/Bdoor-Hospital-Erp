package com.albudoor.hms.premature.listadmissions;

import com.albudoor.hms.premature.api.AdmissionResponse;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/premature/admissions")
@PreAuthorize("isAuthenticated()")
public class ListAdmissionsController {

    private final ListAdmissionsHandler handler;

    public ListAdmissionsController(ListAdmissionsHandler handler) {
        this.handler = handler;
    }

    @GetMapping
    public List<AdmissionResponse> list(
            @RequestParam(value = "status", required = false) AdmissionStatus status) {
        return handler.list(status);
    }
}
