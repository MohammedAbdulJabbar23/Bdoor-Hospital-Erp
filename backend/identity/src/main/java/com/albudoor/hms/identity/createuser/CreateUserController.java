package com.albudoor.hms.identity.createuser;

import com.albudoor.hms.identity.api.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class CreateUserController {

    private final CreateUserHandler handler;

    public CreateUserController(CreateUserHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserCommand cmd) {
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(handler.handle(cmd)));
    }
}
