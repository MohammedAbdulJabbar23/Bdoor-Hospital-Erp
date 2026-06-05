package com.albudoor.hms.identity.resetpassword;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class ResetPasswordController {

    private final ResetPasswordHandler handler;

    public ResetPasswordController(ResetPasswordHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> reset(@PathVariable UUID id, @Valid @RequestBody ResetPasswordCommand cmd) {
        handler.handle(id, cmd);
        return ResponseEntity.noContent().build();
    }
}
