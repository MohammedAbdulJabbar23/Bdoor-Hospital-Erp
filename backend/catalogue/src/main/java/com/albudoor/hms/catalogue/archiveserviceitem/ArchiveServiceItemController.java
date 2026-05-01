package com.albudoor.hms.catalogue.archiveserviceitem;

import com.albudoor.hms.catalogue.api.ServiceItemResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/catalogue/items")
public class ArchiveServiceItemController {

    private final ArchiveServiceItemHandler handler;

    public ArchiveServiceItemController(ArchiveServiceItemHandler handler) {
        this.handler = handler;
    }

    @PutMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceItemResponse archive(@PathVariable UUID id) {
        return ServiceItemResponse.from(handler.archive(id));
    }

    @PutMapping("/{id}/unarchive")
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceItemResponse unarchive(@PathVariable UUID id) {
        return ServiceItemResponse.from(handler.unarchive(id));
    }
}
