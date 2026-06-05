package com.albudoor.hms.identity.updateuser;

import com.albudoor.hms.identity.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Edit an existing user's display name, role assignments, and active status. The username (login
 * key) is intentionally not editable and is therefore absent from this command.
 */
public record UpdateUserCommand(
        @NotBlank @Size(max = 200) String fullName,
        @NotEmpty Set<Role> roles,
        boolean active
) {}
