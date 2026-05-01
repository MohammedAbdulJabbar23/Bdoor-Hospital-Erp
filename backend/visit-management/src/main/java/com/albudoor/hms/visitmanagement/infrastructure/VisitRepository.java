package com.albudoor.hms.visitmanagement.infrastructure;

import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitRepository extends JpaRepository<Visit, UUID> {

    Optional<Visit> findByVisitDisplayId(String displayId);

    List<Visit> findAllByPatientIdOrderByStartedAtDesc(UUID patientId);

    @Query("""
            SELECT v FROM Visit v
            WHERE (:type IS NULL OR v.visitType = :type)
              AND (:status IS NULL OR v.status = :status)
            ORDER BY v.startedAt DESC
            """)
    Page<Visit> search(
            @Param("type") VisitType type,
            @Param("status") VisitStatus status,
            Pageable pageable
    );
}
