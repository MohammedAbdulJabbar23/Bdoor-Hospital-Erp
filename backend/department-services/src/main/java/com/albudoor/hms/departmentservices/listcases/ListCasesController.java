package com.albudoor.hms.departmentservices.listcases;

import com.albudoor.hms.departmentservices.api.DepartmentCaseResponse;
import com.albudoor.hms.departmentservices.domain.DepartmentCase;
import com.albudoor.hms.departmentservices.domain.DepartmentCaseStatus;
import com.albudoor.hms.departmentservices.domain.DepartmentCategory;
import com.albudoor.hms.departmentservices.infrastructure.DepartmentCaseRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dept-cases")
@PreAuthorize("isAuthenticated()")
public class ListCasesController {

    private final DepartmentCaseRepository repo;

    public ListCasesController(DepartmentCaseRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<DepartmentCaseResponse> list(
            @RequestParam("category") DepartmentCategory category,
            @RequestParam(value = "status", required = false) DepartmentCaseStatus status
    ) {
        return repo.findByCategory(category, status).stream()
                .map(DepartmentCaseResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public DepartmentCaseResponse byId(@PathVariable UUID id) {
        DepartmentCase c = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Case not found: " + id));
        return DepartmentCaseResponse.from(c);
    }

    @GetMapping("/by-visit/{visitId}")
    @Transactional(readOnly = true)
    public DepartmentCaseResponse byVisit(@PathVariable UUID visitId) {
        DepartmentCase c = repo.findByVisitId(visitId)
                .orElseThrow(() -> new NotFoundException("No case for visit: " + visitId));
        return DepartmentCaseResponse.from(c);
    }
}
