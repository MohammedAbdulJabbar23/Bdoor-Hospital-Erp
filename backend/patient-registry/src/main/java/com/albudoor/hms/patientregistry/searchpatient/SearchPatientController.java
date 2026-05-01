package com.albudoor.hms.patientregistry.searchpatient;

import com.albudoor.hms.patientregistry.registernewpatient.PatientResponse;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
@PreAuthorize("isAuthenticated()")
public class SearchPatientController {

    private final SearchPatientHandler handler;

    public SearchPatientController(SearchPatientHandler handler) {
        this.handler = handler;
    }

    @GetMapping
    public Page<PatientResponse> search(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return handler.search(query, page, size).map(PatientResponse::from);
    }

    @GetMapping("/{id}")
    public PatientResponse byId(@PathVariable UUID id) {
        return PatientResponse.from(handler.byId(id));
    }

    @GetMapping("/by-mrn/{mrn}")
    public PatientResponse byMrn(@PathVariable String mrn) {
        return PatientResponse.from(handler.byMrn(mrn));
    }
}
