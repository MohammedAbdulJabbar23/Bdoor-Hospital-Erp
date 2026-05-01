package com.albudoor.hms.identity.infrastructure.security;

import com.albudoor.hms.identity.domain.Role;

import java.util.List;
import java.util.UUID;

public record HmsUserPrincipal(
        UUID userId,
        String username,
        String fullName,
        List<Role> roles
) {
    @Override
    public String toString() {
        return username;
    }
}
