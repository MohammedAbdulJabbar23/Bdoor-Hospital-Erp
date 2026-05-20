package com.albudoor.hms.departmentservices.infrastructure;

import com.albudoor.hms.departmentservices.domain.CaseAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CaseAttachmentRepository extends JpaRepository<CaseAttachment, UUID> {
    List<CaseAttachment> findAllByCaseIdOrderByUploadedAtAsc(UUID caseId);
    List<CaseAttachment> findAllByCaseIdAndServiceItemIdOrderByUploadedAtAsc(UUID caseId, UUID serviceItemId);
    long countByCaseIdAndServiceItemId(UUID caseId, UUID serviceItemId);
}
