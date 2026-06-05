package com.albudoor.hms.identity.resetpassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin-initiated password reset for a user. Same length policy as user creation. */
public record ResetPasswordCommand(
        @NotBlank @Size(min = 6, max = 100) String newPassword
) {}
