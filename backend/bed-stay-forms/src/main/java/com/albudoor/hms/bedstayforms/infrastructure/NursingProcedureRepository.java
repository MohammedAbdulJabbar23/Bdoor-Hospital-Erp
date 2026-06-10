package com.albudoor.hms.bedstayforms.infrastructure;

import com.albudoor.hms.bedstayforms.domain.NursingProcedureEntry;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NursingProcedureRepository extends JpaRepository<NursingProcedureEntry, UUID> {
    List<NursingProcedureEntry> findAllByDepartmentAndStayIdOrderByPerformedAtDesc(StayDepartment department, UUID stayId);
}
