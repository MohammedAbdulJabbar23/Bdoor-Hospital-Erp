package com.albudoor.hms.bedstayforms.history;

import com.albudoor.hms.bedstayforms.directory.StayDirectory;
import com.albudoor.hms.bedstayforms.directory.StayRef;
import com.albudoor.hms.bedstayforms.infrastructure.StayDocumentRepository;
import com.albudoor.hms.clinicalcase.history.HistoryContributor;
import com.albudoor.hms.clinicalcase.history.HistoryEntry;
import com.albudoor.hms.clinicalcase.history.HistoryEntryType;
import com.albudoor.hms.clinicalcase.history.HistoryRefs;
import com.albudoor.hms.departmentservices.infrastructure.CaseAttachmentRepository;
import com.albudoor.hms.departmentservices.infrastructure.DepartmentCaseRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Contributes DOCUMENT timeline entries: bed-stay uploads (by patient) and department-case
 * result attachments (via dept-cases by patient). FORM entries for the bed-stay clinical forms
 * are emitted by the premature/emergency contributors, which know the patient's stays.
 */
@Component
public class BedStayFormsHistoryContributor implements HistoryContributor {

    private final StayDocumentRepository documents;
    private final DepartmentCaseRepository deptCases;
    private final CaseAttachmentRepository caseAttachments;
    private final List<StayDirectory> stayDirectories;

    public BedStayFormsHistoryContributor(StayDocumentRepository documents,
                                          DepartmentCaseRepository deptCases,
                                          CaseAttachmentRepository caseAttachments,
                                          List<StayDirectory> stayDirectories) {
        this.documents = documents;
        this.deptCases = deptCases;
        this.caseAttachments = caseAttachments;
        this.stayDirectories = stayDirectories;
    }

    @Override
    public List<HistoryEntry> contribute(UUID patientId) {
        List<HistoryEntry> out = new ArrayList<>();
        documents.findAllByPatientIdOrderByCreatedAtDesc(patientId).forEach(d ->
                out.add(new HistoryEntry(d.getCreatedAt(), HistoryEntryType.DOCUMENT, d.getDepartment().name(),
                        d.getFileName() + (d.getLabel() != null ? " — " + d.getLabel() : ""),
                        d.isArchived() ? "archived" : null,
                        "documentUploaded", Map.of("fileName", d.getFileName()),
                        HistoryRefs.document(d.getId(),
                                "/api/bed-stays/" + d.getDepartment() + "/" + d.getStayId()
                                        + "/documents/" + d.getId() + "/file"))));
        deptCases.findAllByPatientIdOrderByCreatedAtDesc(patientId).forEach(c -> {
            String visitType = c.getCategory().visitType().name();
            // role-correct URL: serve the result on the bed-stay route when the order came from a stay
            Optional<StayRef> stay = stayDirectories.stream()
                    .map(d -> d.stayForOrderVisit(c.getVisitId()))
                    .flatMap(Optional::stream)
                    .findFirst();
            caseAttachments.findAllByCaseIdOrderByUploadedAtAsc(c.getId()).forEach(a -> {
                String fileUrl = stay
                        .map(s -> "/api/bed-stays/" + s.department() + "/" + s.stayId()
                                + "/documents/results/" + a.getId() + "/file")
                        .orElse("/api/dept-cases/attachments/" + a.getId() + "/file");
                out.add(new HistoryEntry(a.getUploadedAt(), HistoryEntryType.DOCUMENT, visitType,
                        a.getFileName(), "Result document",
                        "resultDocument", Map.of("fileName", a.getFileName(), "visitType", visitType),
                        HistoryRefs.document(a.getId(), fileUrl)));
            });
        });
        return out;
    }
}
