package com.albudoor.hms.premature.infrastructure;

import com.albudoor.hms.premature.domain.PrematureTour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrematureTourRepository extends JpaRepository<PrematureTour, UUID> {
    List<PrematureTour> findAllByAdmissionIdOrderByRecordedAtDesc(UUID admissionId);
}
