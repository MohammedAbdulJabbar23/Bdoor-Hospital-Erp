package com.albudoor.hms.identity.login;

import com.albudoor.hms.identity.domain.Role;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LoginResponse(
        String token,
        Instant expiresAt,
        UserSummary user
) {
    public record UserSummary(UUID id, String username, String fullName, List<Role> roles) {}
}
