package com.albudoor.hms.departmentservices.domain;

import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.visitmanagement.domain.VisitType;

/**
 * The three departments that share the Service Department engine: Lab, Radiology, ECO.
 * Maps deterministically to a {@link VisitType} (which routes the workflow) and a
 * {@link ServiceCategory} (which gates the catalogue picker).
 */
public enum DepartmentCategory {
    LAB(VisitType.LABORATORY, ServiceCategory.LAB),
    RADIOLOGY(VisitType.RADIOLOGY, ServiceCategory.IMAGING),
    ECO(VisitType.ECO, ServiceCategory.ECO);

    private final VisitType visitType;
    private final ServiceCategory catalogueCategory;

    DepartmentCategory(VisitType visitType, ServiceCategory catalogueCategory) {
        this.visitType = visitType;
        this.catalogueCategory = catalogueCategory;
    }

    public VisitType visitType() { return visitType; }
    public ServiceCategory catalogueCategory() { return catalogueCategory; }

    public static DepartmentCategory fromVisitType(VisitType vt) {
        for (DepartmentCategory dc : values()) {
            if (dc.visitType == vt) return dc;
        }
        throw new DomainException("VISIT_NOT_DEPT",
                "Visit type " + vt + " is not handled by the service department engine");
    }
}
