package com.albudoor.hms.identity.api;

import com.albudoor.hms.identity.domain.Role;
import com.albudoor.hms.identity.domain.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String fullName,
        boolean active,
        List<Role> roles,
        Instant createdAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(), u.getUsername(), u.getFullName(), u.isActive(),
                u.getRoles().stream().sorted().toList(),
                u.getCreatedAt());
    }
}
