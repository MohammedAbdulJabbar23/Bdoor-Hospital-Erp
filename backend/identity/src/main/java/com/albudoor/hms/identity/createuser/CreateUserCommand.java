package com.albudoor.hms.identity.createuser;

import com.albudoor.hms.identity.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserCommand(
        @NotBlank @Size(min = 3, max = 100) String username,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotBlank @Size(max = 200) String fullName,
        @NotEmpty Set<Role> roles
) {}
