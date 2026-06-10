package com.albudoor.hms.departmentservices.inventory;

import com.albudoor.hms.departmentservices.infrastructure.CaseAttachmentRepository;
import com.albudoor.hms.platform.storage.inventory.DocumentInventoryContributor;
import com.albudoor.hms.platform.storage.inventory.DocumentRef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DeptServicesInventoryContributor implements DocumentInventoryContributor {

    private final CaseAttachmentRepository attachments;

    public DeptServicesInventoryContributor(CaseAttachmentRepository attachments) {
        this.attachments = attachments;
    }

    @Override
    public List<DocumentRef> documentRefs() {
        List<DocumentRef> out = new ArrayList<>();
        attachments.findAll().forEach(a ->
                out.add(new DocumentRef("case_attachment", a.getId().toString(), a.getStorageKey(), null)));
        return out;
    }
}
