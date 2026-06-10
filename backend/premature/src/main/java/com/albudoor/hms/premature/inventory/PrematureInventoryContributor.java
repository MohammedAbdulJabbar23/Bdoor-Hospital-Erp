package com.albudoor.hms.premature.inventory;

import com.albudoor.hms.platform.storage.inventory.DocumentInventoryContributor;
import com.albudoor.hms.platform.storage.inventory.DocumentRef;
import com.albudoor.hms.premature.domain.SignatureSlot;
import com.albudoor.hms.premature.infrastructure.PrematureFormRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PrematureInventoryContributor implements DocumentInventoryContributor {

    private final PrematureFormRepository forms;

    public PrematureInventoryContributor(PrematureFormRepository forms) {
        this.forms = forms;
    }

    @Override
    public List<DocumentRef> documentRefs() {
        List<DocumentRef> out = new ArrayList<>();
        forms.findAll().forEach(f -> {
            for (SignatureSlot slot : SignatureSlot.values()) {
                var sig = f.signature(slot);
                if (sig != null && sig.getImageKey() != null) {
                    out.add(new DocumentRef("prem_form:" + slot, f.getId().toString(), sig.getImageKey(), null));
                }
            }
        });
        return out;
    }
}
