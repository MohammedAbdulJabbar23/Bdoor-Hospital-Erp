package com.albudoor.hms.bedstayforms.documents;

import com.albudoor.hms.bedstayforms.api.OrderResultsResponse;
import com.albudoor.hms.bedstayforms.api.OrderResultsResponse.ServiceFinding;
import com.albudoor.hms.bedstayforms.api.StayDocumentDto;
import com.albudoor.hms.bedstayforms.directory.StayDirectoryRegistry;
import com.albudoor.hms.bedstayforms.directory.StayOrderRef;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.departmentservices.domain.CaseServiceLine;
import com.albudoor.hms.departmentservices.domain.DepartmentCase;
import com.albudoor.hms.departmentservices.infrastructure.CaseAttachmentRepository;
import com.albudoor.hms.departmentservices.infrastructure.DepartmentCaseRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderResultsHandler {

    private final StayDirectoryRegistry stays;
    private final DepartmentCaseRepository deptCases;
    private final CaseAttachmentRepository caseAttachments;

    public OrderResultsHandler(StayDirectoryRegistry stays, DepartmentCaseRepository deptCases,
                               CaseAttachmentRepository caseAttachments) {
        this.stays = stays;
        this.deptCases = deptCases;
        this.caseAttachments = caseAttachments;
    }

    @Transactional(readOnly = true)
    public OrderResultsResponse results(StayDepartment dept, UUID stayId, UUID visitId) {
        stays.require(dept, stayId);
        // ownership walk mirrors StayDocumentsHandler.requireResultAttachment: the visit
        // must be one of THIS stay's forwarded orders, else 404
        StayOrderRef order = stays.directory(dept).orders(stayId).stream()
                .filter(o -> o.visitId().equals(visitId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Order not found on this stay: " + visitId));

        DepartmentCase deptCase = deptCases.findByVisitId(visitId).orElse(null);
        if (deptCase == null) {
            // ordered, but the receiving department has not opened its case yet
            return new OrderResultsResponse(List.of(), List.of());
        }

        List<ServiceFinding> services = new ArrayList<>();
        for (CaseServiceLine line : deptCase.getServices()) {
            services.add(new ServiceFinding(line.getServiceName(), line.getTextFindings()));
        }

        String base = "/api/bed-stays/" + dept + "/" + stayId + "/documents/";
        List<StayDocumentDto> documents = new ArrayList<>();
        caseAttachments.findAllByCaseIdOrderByUploadedAtAsc(deptCase.getId()).forEach(a ->
                documents.add(StayDocumentDto.fromResultAttachment(a.getId(), order.targetType(),
                        a.getFileName(), a.getContentType(), a.getSizeBytes(),
                        a.getUploadedBy(), a.getUploadedAt(),
                        base + "results/" + a.getId() + "/file")));

        return new OrderResultsResponse(services, documents);
    }
}
