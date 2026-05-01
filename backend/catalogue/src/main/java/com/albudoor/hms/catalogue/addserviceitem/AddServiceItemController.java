package com.albudoor.hms.catalogue.addserviceitem;

import com.albudoor.hms.catalogue.api.ServiceItemResponse;
import com.albudoor.hms.catalogue.domain.ServiceItem;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalogue/items")
public class AddServiceItemController {

    private final AddServiceItemHandler handler;

    public AddServiceItemController(AddServiceItemHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceItemResponse> add(@Valid @RequestBody AddServiceItemCommand cmd) {
        ServiceItem saved = handler.handle(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(ServiceItemResponse.from(saved));
    }
}
