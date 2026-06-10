package com.albudoor.hms.bedstayforms.access;

import com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class CurrentUser {
    private CurrentUser() {}

    public static UUID id() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof HmsUserPrincipal p) return p.userId();
        return null;
    }

    /** Display name for auto-attribution (nursing rows): full name, falling back to username. */
    public static String displayName() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof HmsUserPrincipal p) {
            return (p.fullName() != null && !p.fullName().isBlank()) ? p.fullName() : p.username();
        }
        return a == null ? null : a.getName();
    }
}
