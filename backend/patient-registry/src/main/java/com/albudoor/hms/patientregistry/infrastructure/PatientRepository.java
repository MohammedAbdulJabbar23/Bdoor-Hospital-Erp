package com.albudoor.hms.patientregistry.infrastructure;

import com.albudoor.hms.patientregistry.domain.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByMrn(String mrn);

    boolean existsByMrn(String mrn);

    /** True if any (non-archived or archived) patient already carries this adult national ID. */
    boolean existsByAdultDetails_NationalId(String nationalId);

    /**
     * Full-text-ish patient search. The {@code :q} value MUST be pre-escaped for LIKE
     * metacharacters by the caller ({@code SearchPatientHandler}); the ESCAPE clause below
     * tells the DB that {@code \} is the escape char, so a literal {@code %} or {@code _} in
     * the query no longer acts as a wildcard (e.g. {@code q=%} won't dump every patient).
     */
    @Query("""
            SELECT p FROM Patient p
            WHERE p.archived = false
              AND (:q IS NULL OR :q = ''
                   OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
                   OR p.mrn LIKE CONCAT('%', :q, '%') ESCAPE '\\'
                   OR p.adultDetails.mobileNumber LIKE CONCAT('%', :q, '%') ESCAPE '\\'
                   OR p.adultDetails.nationalId LIKE CONCAT('%', :q, '%') ESCAPE '\\'
                   OR p.infantDetails.motherMobile LIKE CONCAT('%', :q, '%') ESCAPE '\\'
                   OR p.infantDetails.motherNationalId LIKE CONCAT('%', :q, '%') ESCAPE '\\')
            ORDER BY p.createdAt DESC
            """)
    Page<Patient> search(@Param("q") String query, Pageable pageable);
}
