package com.albudoor.hms.bedstayforms.access;

import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Department-scoped authorization: <DEPT>_STAFF may act only on their own department's
 * stays; DOCTOR/NURSE/ADMIN are hospital-wide, with write level per the BRD actor table
 * (doctors own medical history + treatment chart, nurses own the procedures log).
 * A @PreAuthorize can't see the {department} path variable, hence this runtime check.
 */
@Component
public class BedStayAccess {

    public void checkRead(StayDepartment dept) { check(dept, "ROLE_DOCTOR", "ROLE_NURSE", "ROLE_ADMIN"); }
    public void checkDoctorWrite(StayDepartment dept) { check(dept, "ROLE_DOCTOR", "ROLE_ADMIN"); }
    public void checkNurseWrite(StayDepartment dept) { check(dept, "ROLE_NURSE", "ROLE_ADMIN"); }

    private void check(StayDepartment dept, String... globalRoles) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) throw new AccessDeniedException("Not authenticated");
        Set<String> roles = a.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        if (roles.contains("ROLE_" + dept.name() + "_STAFF")) return;
        for (String r : globalRoles) if (roles.contains(r)) return;
        throw new AccessDeniedException("Not permitted for " + dept + " stays");
    }
}
