package com.albudoor.hms.emergency.infrastructure;

import com.albudoor.hms.emergency.domain.BedStatus;
import com.albudoor.hms.emergency.domain.EmergencyBed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmergencyBedRepository extends JpaRepository<EmergencyBed, UUID> {

    boolean existsByCode(String code);

    Optional<EmergencyBed> findByCode(String code);

    List<EmergencyBed> findAllByOrderByCodeAsc();

    long countByStatus(BedStatus status);

    long countByActiveTrue();
}
