package com.albudoor.hms.bedstayforms.infrastructure;

import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.domain.StayDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StayDocumentRepository extends JpaRepository<StayDocument, UUID> {
    List<StayDocument> findAllByDepartmentAndStayIdOrderByCreatedAtDesc(StayDepartment department, UUID stayId);
    List<StayDocument> findAllByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
