package com.albudoor.hms.departmentservices.finalize;

import com.albudoor.hms.departmentservices.domain.DepartmentCase;
import com.albudoor.hms.departmentservices.domain.DepartmentCaseStatus;
import com.albudoor.hms.departmentservices.infrastructure.DepartmentCaseRepository;
import com.albudoor.hms.departmentservices.security.DepartmentRoleGuard;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Closes a department case once all findings are uploaded.
 *
 * <ul>
 *   <li>Direct visits ({@code DIRECT_NEW} / {@code DIRECT_RETURNING}) — case → CLOSED,
 *       linked visit → COMPLETED.</li>
 *   <li>Forwarded visits — case → RETURNED, sub-visit → COMPLETED, parent visit gets
 *       results back via {@link Visit#receiveResultsFromChild}.</li>
 * </ul>
 */
@Service
public class FinalizeCaseHandler {

    private final DepartmentCaseRepository cases;
    private final VisitRepository visits;
    private final ApplicationEventPublisher events;
    private final DepartmentRoleGuard roleGuard;

    public FinalizeCaseHandler(
            DepartmentCaseRepository cases,
            VisitRepository visits,
            ApplicationEventPublisher events,
            DepartmentRoleGuard roleGuard
    ) {
        this.cases = cases;
        this.visits = visits;
        this.events = events;
        this.roleGuard = roleGuard;
    }

    @Transactional
    public DepartmentCase handle(UUID caseId) {
        DepartmentCase deptCase = cases.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Case not found: " + caseId));

        // The caller's department role must match this case's category (ADMIN bypasses).
        roleGuard.requireDepartmentMatches(deptCase.getCategory());

        String summary = deptCase.buildResultsSummary();

        Visit subVisit = visits.findById(deptCase.getVisitId())
                .orElseThrow(() -> new NotFoundException("Visit not found: " + deptCase.getVisitId()));

        if (deptCase.isForwarded()) {
            // Forwarded — return to origin
            Visit parent = visits.findById(deptCase.getParentVisitId())
                    .orElseThrow(() -> new NotFoundException(
                            "Parent visit not found: " + deptCase.getParentVisitId()));
            subVisit.completeForwardedWith(summary);
            parent.receiveResultsFromChild(subVisit.getId(), subVisit.getVisitType(), summary);
            subVisit.pullDomainEvents().forEach(events::publishEvent);
            parent.pullDomainEvents().forEach(events::publishEvent);
            deptCase.markFinalized(DepartmentCaseStatus.RETURNED, summary);
        } else {
            // Direct visit — close
            if (subVisit.getStatus() != VisitStatus.COMPLETED) {
                subVisit.transitionTo(VisitStatus.COMPLETED);
                subVisit.pullDomainEvents().forEach(events::publishEvent);
            }
            deptCase.markFinalized(DepartmentCaseStatus.CLOSED, summary);
        }
        return deptCase;
    }
}
