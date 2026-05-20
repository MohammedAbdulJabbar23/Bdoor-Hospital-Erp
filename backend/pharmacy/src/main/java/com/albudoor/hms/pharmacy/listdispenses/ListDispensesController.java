package com.albudoor.hms.pharmacy.listdispenses;

import com.albudoor.hms.pharmacy.api.DispenseResponse;
import com.albudoor.hms.pharmacy.domain.DispenseStatus;
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
@RequestMapping("/api/dispenses")
@PreAuthorize("isAuthenticated()")
public class ListDispensesController {

    private final ListDispensesHandler handler;

    public ListDispensesController(ListDispensesHandler handler) {
        this.handler = handler;
    }

    @GetMapping
    public Page<DispenseResponse> search(
            @RequestParam(value = "status", required = false) DispenseStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return handler.search(status, page, size).map(DispenseResponse::from);
    }

    @GetMapping("/{id}")
    public DispenseResponse byId(@PathVariable UUID id) {
        return DispenseResponse.from(handler.byId(id));
    }

    @GetMapping("/by-patient/{patientId}")
    public List<DispenseResponse> byPatient(@PathVariable UUID patientId) {
        return handler.byPatient(patientId).stream().map(DispenseResponse::from).toList();
    }
}
