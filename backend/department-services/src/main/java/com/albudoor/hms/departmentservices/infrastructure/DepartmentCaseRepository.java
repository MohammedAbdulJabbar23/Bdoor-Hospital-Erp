package com.albudoor.hms.departmentservices.infrastructure;

import com.albudoor.hms.departmentservices.domain.DepartmentCase;
import com.albudoor.hms.departmentservices.domain.DepartmentCaseStatus;
import com.albudoor.hms.departmentservices.domain.DepartmentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentCaseRepository extends JpaRepository<DepartmentCase, UUID> {

    Optional<DepartmentCase> findByVisitId(UUID visitId);

    Optional<DepartmentCase> findByPaymentId(UUID paymentId);

    @Query("""
            SELECT c FROM DepartmentCase c
            WHERE c.category = :category
              AND (:status IS NULL OR c.status = :status)
            ORDER BY c.createdAt DESC
            """)
    List<DepartmentCase> findByCategory(
            @Param("category") DepartmentCategory category,
            @Param("status") DepartmentCaseStatus status
    );

    List<DepartmentCase> findAllByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
