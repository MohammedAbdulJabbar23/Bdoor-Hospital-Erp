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

    @Query("""
            SELECT p FROM Patient p
            WHERE p.archived = false
              AND (:q IS NULL OR :q = ''
                   OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR p.mrn LIKE CONCAT('%', :q, '%')
                   OR p.adultDetails.mobileNumber LIKE CONCAT('%', :q, '%')
                   OR p.adultDetails.nationalId LIKE CONCAT('%', :q, '%')
                   OR p.infantDetails.motherMobile LIKE CONCAT('%', :q, '%')
                   OR p.infantDetails.motherNationalId LIKE CONCAT('%', :q, '%'))
            ORDER BY p.createdAt DESC
            """)
    Page<Patient> search(@Param("q") String query, Pageable pageable);
}
