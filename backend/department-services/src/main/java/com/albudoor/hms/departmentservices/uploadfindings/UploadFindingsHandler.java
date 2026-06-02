package com.albudoor.hms.departmentservices.uploadfindings;

import com.albudoor.hms.departmentservices.domain.DepartmentCase;
import com.albudoor.hms.departmentservices.infrastructure.DepartmentCaseRepository;
import com.albudoor.hms.departmentservices.security.DepartmentRoleGuard;
import com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UploadFindingsHandler {

    private final DepartmentCaseRepository cases;
    private final DepartmentRoleGuard roleGuard;

    public UploadFindingsHandler(DepartmentCaseRepository cases, DepartmentRoleGuard roleGuard) {
        this.cases = cases;
        this.roleGuard = roleGuard;
    }

    @Transactional
    public DepartmentCase handle(UUID caseId, UploadFindingsCommand cmd) {
        DepartmentCase deptCase = cases.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Case not found: " + caseId));

        // The caller's department role must match this case's category (ADMIN bypasses).
        roleGuard.requireDepartmentMatches(deptCase.getCategory());

        UUID userId = currentUserId();
        deptCase.uploadFindings(
                cmd.serviceItemId(),
                blankToNull(cmd.textFindings()),
                cmd.numericValue(),
                blankToNull(cmd.unit()),
                blankToNull(cmd.referenceRange()),
                blankToNull(cmd.flag()),
                blankToNull(cmd.measurements()),
                blankToNull(cmd.bodyRegion()),
                blankToNull(cmd.comments()),
                blankToNull(cmd.diagnosis()),
                userId
        );
        return deptCase;
    }

    private static UUID currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof HmsUserPrincipal p) return p.userId();
        return null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
