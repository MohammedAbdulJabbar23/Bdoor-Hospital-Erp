package com.albudoor.hms.departmentservices.security;

import com.albudoor.hms.departmentservices.domain.DepartmentCategory;
import com.albudoor.hms.identity.domain.Role;
import com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Defence-in-depth for the Lab/Radiology/ECO department engine.
 *
 * <p>The {@code @PreAuthorize} on the open/upload/finalize endpoints only proves the caller
 * holds <em>some</em> department-staff role. It does NOT prove the caller's department matches
 * the case's {@link DepartmentCategory}. Without this check a {@code LAB_STAFF} user could
 * finalize or write findings on a RADIOLOGY case (and vice-versa) — a real PHI/role gap.
 *
 * <p>This guard enforces that a caller who holds a department-staff role
 * ({@code LAB_STAFF}/{@code RADIOLOGY_STAFF}/{@code ECO_STAFF}) is acting within their own
 * department: {@code LAB_STAFF↔LAB}, {@code RADIOLOGY_STAFF↔RADIOLOGY}, {@code ECO_STAFF↔ECO}.
 *
 * <p>Pure referrers — a {@code DOCTOR}/{@code EMERGENCY_STAFF}/{@code PREMATURE_STAFF} who holds
 * no department-staff role — are intentionally NOT blocked here: forwarding a patient to another
 * department (which opens a cross-department case via {@code forward-with-tests}) is a core BRD
 * referral flow. ADMIN bypasses entirely. The findings/finalize endpoints are already
 * {@code @PreAuthorize}-restricted to department staff + ADMIN, so for those operations this
 * guard fully enforces same-department access.
 */
@Component
public class DepartmentRoleGuard {

    private static final List<Role> DEPARTMENT_ROLES =
            List.of(Role.LAB_STAFF, Role.RADIOLOGY_STAFF, Role.ECO_STAFF);

    /**
     * @throws AccessDeniedException (→ HTTP 403) when the caller holds a department-staff role
     *         that does not match {@code category} (and is not an ADMIN). Callers with no
     *         department-staff role (e.g. a referring DOCTOR) and ADMINs are allowed through.
     */
    public void requireDepartmentMatches(DepartmentCategory category) {
        List<Role> roles = currentRoles();
        if (roles.contains(Role.ADMIN)) {
            return;
        }
        boolean holdsDepartmentRole = roles.stream().anyMatch(DEPARTMENT_ROLES::contains);
        if (!holdsDepartmentRole) {
            // A pure referrer (e.g. a doctor forwarding tests) — not a department-staff user.
            return;
        }
        Role required = requiredRoleFor(category);
        if (!roles.contains(required)) {
            throw new AccessDeniedException(
                    "Caller is not assigned to the " + category + " department");
        }
    }

    private static Role requiredRoleFor(DepartmentCategory category) {
        return switch (category) {
            case LAB -> Role.LAB_STAFF;
            case RADIOLOGY -> Role.RADIOLOGY_STAFF;
            case ECO -> Role.ECO_STAFF;
        };
    }

    private static List<Role> currentRoles() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof HmsUserPrincipal p) {
            return p.roles();
        }
        return List.of();
    }
}
