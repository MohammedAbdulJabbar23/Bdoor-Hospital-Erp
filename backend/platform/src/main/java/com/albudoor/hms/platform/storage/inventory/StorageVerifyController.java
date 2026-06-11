package com.albudoor.hms.platform.storage.inventory;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/storage")
public class StorageVerifyController {

    private final StorageVerifyHandler handler;

    public StorageVerifyController(StorageVerifyHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public StorageVerifyResponse verify() {
        return handler.verify();
    }
}
