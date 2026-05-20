package com.albudoor.hms.identity.listusers;

import com.albudoor.hms.identity.api.UserResponse;
import com.albudoor.hms.identity.domain.User;
import com.albudoor.hms.identity.infrastructure.UserRepository;
import com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class ListUsersController {

    private final UserRepository repo;

    public ListUsersController(UserRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> list() {
        return repo.findAll().stream()
                .sorted((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()))
                .map(UserResponse::from)
                .toList();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserResponse me(@AuthenticationPrincipal HmsUserPrincipal principal) {
        if (principal == null) throw new NotFoundException("No authenticated user");
        return UserResponse.from(load(principal.userId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse byId(@PathVariable UUID id) {
        return UserResponse.from(load(id));
    }

    private User load(UUID id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("User not found: " + id));
    }
}
