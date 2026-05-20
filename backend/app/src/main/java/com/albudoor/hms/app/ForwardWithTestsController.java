package com.albudoor.hms.app;

import com.albudoor.hms.departmentservices.domain.DepartmentCategory;
import com.albudoor.hms.departmentservices.opencase.OpenCaseCommand;
import com.albudoor.hms.departmentservices.opencase.OpenCaseHandler;
import com.albudoor.hms.visitmanagement.api.VisitResponse;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import com.albudoor.hms.visitmanagement.forwardvisit.ForwardVisitCommand;
import com.albudoor.hms.visitmanagement.forwardvisit.ForwardVisitHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * One-shot orchestrator: forward a doctor visit to a department AND pre-select the tests in
 * a single transaction. Addresses the HMS punch-list item that asked doctors to be able to
 * choose tests at the moment of forwarding (so the lab staff don't have to re-pick).
 *
 * <p>The orchestration lives in {@code app/} because it crosses two modules
 * ({@code visit-management} and {@code department-services}).
 */
@RestController
@RequestMapping("/api/visits")
public class ForwardWithTestsController {

    public record ForwardWithTestsBody(
            @NotNull VisitType targetType,
            @NotEmpty List<Service> services
    ) {
        public record Service(
                @NotNull UUID serviceItemId,
                @Positive int quantity
        ) {}
    }

    public record ForwardWithTestsResponse(
            VisitResponse parent,
            VisitResponse child,
            UUID caseId
    ) {}

    private final ForwardVisitHandler forwardVisit;
    private final OpenCaseHandler openCase;

    public ForwardWithTestsController(
            ForwardVisitHandler forwardVisit,
            OpenCaseHandler openCase
    ) {
        this.forwardVisit = forwardVisit;
        this.openCase = openCase;
    }

    @PostMapping("/{id}/forward-with-tests")
    @PreAuthorize("hasAnyRole('DOCTOR', 'EMERGENCY_STAFF', 'PREMATURE_STAFF', 'ADMIN')")
    @Transactional
    public ForwardWithTestsResponse forward(
            @PathVariable UUID id,
            @Valid @RequestBody ForwardWithTestsBody body
    ) {
        ForwardVisitHandler.ForwardResult result =
                forwardVisit.handle(id, new ForwardVisitCommand(body.targetType()));

        DepartmentCategory category = DepartmentCategory.fromVisitType(body.targetType());
        List<OpenCaseCommand.Service> services = body.services().stream()
                .map(s -> new OpenCaseCommand.Service(s.serviceItemId(), s.quantity()))
                .toList();
        var deptCase = openCase.handle(new OpenCaseCommand(
                category, result.child().getId(), services));

        return new ForwardWithTestsResponse(
                VisitResponse.from(result.parent()),
                VisitResponse.from(result.child()),
                deptCase.getId());
    }
}
