package com.albudoor.hms.catalogue.addserviceitem;

import com.albudoor.hms.catalogue.domain.DrugDetails;
import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.catalogue.domain.ServiceItem;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.platform.exception.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddServiceItemHandler {

    private static final String DEFAULT_CURRENCY = "IQD";

    private final ServiceItemRepository repo;

    public AddServiceItemHandler(ServiceItemRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public ServiceItem handle(AddServiceItemCommand cmd) {
        // Normalise the code the same way ServiceItem.create stores it (trim) BEFORE checking
        // uniqueness, and check case-insensitively, so " PRB1 " can't bypass the guard (→ 500
        // on the DB constraint) and "prb1"/"PRB1" can't coexist in one category.
        String code = cmd.code() == null ? null : cmd.code().trim();
        if (code != null && repo.existsByCategoryAndCodeIgnoreCase(cmd.category(), code)) {
            throw new ConflictException("SERVICE_CODE_EXISTS",
                    "Code '" + code + "' already exists in category " + cmd.category());
        }

        DrugDetails drugDetails = null;
        if (cmd.category() == ServiceCategory.DRUG) {
            AddServiceItemCommand.DrugPart d = cmd.drug();
            if (d == null) {
                drugDetails = new DrugDetails(null, null, null, null, false, null, null);
            } else {
                drugDetails = new DrugDetails(
                        d.genericName(), d.dosageForm(), d.strength(),
                        d.unit(), d.controlled(), d.supplier(), d.barcode());
            }
        }

        ServiceItem item = ServiceItem.create(
                cmd.category(),
                code,
                cmd.nameEn(),
                cmd.nameAr(),
                cmd.description(),
                cmd.fee(),
                cmd.currency() == null || cmd.currency().isBlank() ? DEFAULT_CURRENCY : cmd.currency(),
                cmd.sortOrder(),
                cmd.forwardTo(),
                drugDetails
        );
        return repo.save(item);
    }
}
