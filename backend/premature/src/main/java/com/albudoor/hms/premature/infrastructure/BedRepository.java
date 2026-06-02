package com.albudoor.hms.premature.infrastructure;

import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.domain.BedStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BedRepository extends JpaRepository<Bed, UUID> {

    boolean existsByCode(String code);

    Optional<Bed> findByCode(String code);

    List<Bed> findAllByOrderByCodeAsc();

    long countByStatus(BedStatus status);

    long countByActiveTrue();
}
