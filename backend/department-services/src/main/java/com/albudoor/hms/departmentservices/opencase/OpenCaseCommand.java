package com.albudoor.hms.departmentservices.opencase;

import com.albudoor.hms.departmentservices.domain.DepartmentCategory;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

/**
 * Open a department case for an existing visit, pick services, and route the resulting
 * payment to the central cashier in one atomic step.
 *
 * <p>If the visit already has a case, the supplied services are appended (subject to the
 * usual no-duplicates / lifecycle rules).
 */
public record OpenCaseCommand(
        @NotNull DepartmentCategory category,
        @NotNull UUID visitId,
        @NotEmpty List<Service> services
) {
    public record Service(
            @NotNull UUID serviceItemId,
            @Positive int quantity
    ) {}
}
