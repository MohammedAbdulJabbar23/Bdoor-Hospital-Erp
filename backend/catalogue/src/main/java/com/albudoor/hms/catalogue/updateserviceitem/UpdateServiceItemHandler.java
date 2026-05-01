package com.albudoor.hms.catalogue.updateserviceitem;

import com.albudoor.hms.catalogue.addserviceitem.AddServiceItemCommand;
import com.albudoor.hms.catalogue.domain.DrugDetails;
import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.catalogue.domain.ServiceItem;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateServiceItemHandler {

    private final ServiceItemRepository repo;

    public UpdateServiceItemHandler(ServiceItemRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public ServiceItem handle(UUID id, UpdateServiceItemCommand cmd) {
        ServiceItem item = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Service item not found: " + id));

        DrugDetails drugDetails = item.getDrugDetails();
        if (item.getCategory() == ServiceCategory.DRUG) {
            AddServiceItemCommand.DrugPart d = cmd.drug();
            if (d != null) {
                drugDetails = new DrugDetails(
                        d.genericName(), d.dosageForm(), d.strength(),
                        d.unit(), d.controlled(), d.supplier(), d.barcode());
            }
        }

        item.update(
                cmd.nameEn(),
                cmd.nameAr(),
                cmd.description(),
                cmd.fee(),
                cmd.currency(),
                cmd.sortOrder(),
                cmd.forwardTo(),
                drugDetails
        );
        return item;
    }
}
