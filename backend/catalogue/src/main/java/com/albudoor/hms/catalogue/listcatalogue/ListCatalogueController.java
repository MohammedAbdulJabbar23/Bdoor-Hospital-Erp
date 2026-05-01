package com.albudoor.hms.catalogue.listcatalogue;

import com.albudoor.hms.catalogue.api.ServiceItemResponse;
import com.albudoor.hms.catalogue.domain.ServiceCategory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalogue/items")
@PreAuthorize("isAuthenticated()")
public class ListCatalogueController {

    private final ListCatalogueHandler handler;

    public ListCatalogueController(ListCatalogueHandler handler) {
        this.handler = handler;
    }

    @GetMapping
    public List<ServiceItemResponse> list(
            @RequestParam("category") ServiceCategory category,
            @RequestParam(value = "activeOnly", required = false) Boolean activeOnly
    ) {
        return handler.list(category, activeOnly).stream()
                .map(ServiceItemResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ServiceItemResponse byId(@PathVariable UUID id) {
        return ServiceItemResponse.from(handler.byId(id));
    }
}
