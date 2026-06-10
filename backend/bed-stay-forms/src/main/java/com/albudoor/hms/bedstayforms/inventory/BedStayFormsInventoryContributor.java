package com.albudoor.hms.bedstayforms.inventory;

import com.albudoor.hms.bedstayforms.domain.MhSignatureSlot;
import com.albudoor.hms.bedstayforms.infrastructure.MedicalHistoryRepository;
import com.albudoor.hms.bedstayforms.infrastructure.StayDocumentRepository;
import com.albudoor.hms.bedstayforms.infrastructure.TreatmentChartRepository;
import com.albudoor.hms.platform.storage.inventory.DocumentInventoryContributor;
import com.albudoor.hms.platform.storage.inventory.DocumentRef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BedStayFormsInventoryContributor implements DocumentInventoryContributor {

    private final StayDocumentRepository documents;
    private final MedicalHistoryRepository sheets;
    private final TreatmentChartRepository charts;

    public BedStayFormsInventoryContributor(StayDocumentRepository documents,
                                            MedicalHistoryRepository sheets,
                                            TreatmentChartRepository charts) {
        this.documents = documents;
        this.sheets = sheets;
        this.charts = charts;
    }

    @Override
    public List<DocumentRef> documentRefs() {
        List<DocumentRef> out = new ArrayList<>();
        documents.findAll().forEach(d ->
                out.add(new DocumentRef("stay_document", d.getId().toString(), d.getStorageKey(), d.getSha256())));
        sheets.findAll().forEach(s -> {
            for (MhSignatureSlot slot : MhSignatureSlot.values()) {
                var sig = s.signature(slot);
                if (sig.present()) out.add(new DocumentRef("stay_medical_history:" + slot,
                        s.getId().toString(), sig.getImageKey(), null));
            }
        });
        charts.findAll().forEach(c -> {
            if (c.getDoctorSignature().present()) out.add(new DocumentRef("stay_treatment_chart",
                    c.getId().toString(), c.getDoctorSignature().getImageKey(), null));
        });
        return out;
    }
}
