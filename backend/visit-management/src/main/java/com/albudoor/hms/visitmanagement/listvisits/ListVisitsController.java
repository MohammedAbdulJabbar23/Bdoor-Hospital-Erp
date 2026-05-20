package com.albudoor.hms.visitmanagement.listvisits;

import com.albudoor.hms.visitmanagement.api.VisitResponse;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/visits")
@PreAuthorize("isAuthenticated()")
public class ListVisitsController {

    private final ListVisitsHandler handler;

    public ListVisitsController(ListVisitsHandler handler) {
        this.handler = handler;
    }

    @GetMapping
    public Page<VisitResponse> search(
            @RequestParam(value = "type", required = false) VisitType type,
            @RequestParam(value = "status", required = false) VisitStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return handler.search(type, status, page, size).map(VisitResponse::from);
    }

    @GetMapping("/{id}")
    public VisitResponse byId(@PathVariable UUID id) {
        return VisitResponse.from(handler.byId(id));
    }

    @GetMapping("/by-patient/{patientId}")
    public List<VisitResponse> byPatient(@PathVariable UUID patientId) {
        return handler.byPatient(patientId).stream().map(VisitResponse::from).toList();
    }

    @GetMapping("/{id}/children")
    public List<VisitResponse> children(@PathVariable UUID id) {
        return handler.children(id).stream().map(VisitResponse::from).toList();
    }
}
