package com.albudoor.hms.visitmanagement.infrastructure;

import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitRepository extends JpaRepository<Visit, UUID> {

    Optional<Visit> findByVisitDisplayId(String displayId);

    /** Number of distinct patients with at least one visit started in the given window. */
    @Query("""
            SELECT COUNT(DISTINCT v.patientId) FROM Visit v
            WHERE v.startedAt >= :from AND v.startedAt < :to
            """)
    long countDistinctPatientsStartedBetween(
            @Param("from") Instant from, @Param("to") Instant to);

    /** Number of distinct visit types that currently have at least one visit in the given statuses. */
    @Query("""
            SELECT COUNT(DISTINCT v.visitType) FROM Visit v
            WHERE v.status IN :statuses
            """)
    long countDistinctVisitTypesByStatusIn(@Param("statuses") List<VisitStatus> statuses);

    List<Visit> findAllByPatientIdOrderByStartedAtDesc(UUID patientId);

    List<Visit> findAllByParentVisitIdOrderByStartedAtDesc(UUID parentVisitId);

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

    /**
     * Per-status visit counts across the whole table (or filtered to one type), computed by the DB
     * via GROUP BY — never by paging+summing — so the queue KPIs are correct regardless of how many
     * visits exist. Each row is {@code [VisitStatus status, Long count]}.
     */
    @Query("""
            SELECT v.status, COUNT(v) FROM Visit v
            WHERE (:type IS NULL OR v.visitType = :type)
            GROUP BY v.status
            """)
    List<Object[]> countByStatusGrouped(@Param("type") VisitType type);

    /**
     * Number of visits COMPLETED in the given window (by {@code endedAt}), optionally filtered to
     * one type. Drives the "Completed today" KPI — counting only visits that actually ended today,
     * not every COMPLETED visit ever.
     */
    @Query("""
            SELECT COUNT(v) FROM Visit v
            WHERE v.status = com.albudoor.hms.visitmanagement.domain.VisitStatus.COMPLETED
              AND v.endedAt >= :from AND v.endedAt < :to
              AND (:type IS NULL OR v.visitType = :type)
            """)
    long countCompletedBetween(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("type") VisitType type
    );
}
