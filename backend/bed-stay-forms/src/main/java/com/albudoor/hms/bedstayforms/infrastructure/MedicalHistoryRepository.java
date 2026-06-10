package com.albudoor.hms.bedstayforms.infrastructure;

import com.albudoor.hms.bedstayforms.domain.MedicalHistorySheet;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MedicalHistoryRepository extends JpaRepository<MedicalHistorySheet, UUID> {
    Optional<MedicalHistorySheet> findByDepartmentAndStayId(StayDepartment department, UUID stayId);
}
