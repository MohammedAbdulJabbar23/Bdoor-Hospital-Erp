package com.albudoor.hms.catalogue.updateserviceitem;

import com.albudoor.hms.catalogue.api.ServiceItemResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/catalogue/items")
public class UpdateServiceItemController {

    private final UpdateServiceItemHandler handler;

    public UpdateServiceItemController(UpdateServiceItemHandler handler) {
        this.handler = handler;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceItemResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateServiceItemCommand cmd) {
        return ServiceItemResponse.from(handler.handle(id, cmd));
    }
}
