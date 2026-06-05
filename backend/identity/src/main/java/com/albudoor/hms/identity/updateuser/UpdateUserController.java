package com.albudoor.hms.identity.updateuser;

import com.albudoor.hms.identity.api.UserResponse;
import com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UpdateUserController {

    private final UpdateUserHandler handler;

    public UpdateUserController(UpdateUserHandler handler) {
        this.handler = handler;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserCommand cmd,
            @AuthenticationPrincipal HmsUserPrincipal principal
    ) {
        return UserResponse.from(handler.handle(id, cmd, principal.userId()));
    }
}
