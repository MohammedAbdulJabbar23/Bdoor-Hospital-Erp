package com.albudoor.hms.catalogue.listcatalogue;

import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.catalogue.domain.ServiceItem;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ListCatalogueHandler {

    private final ServiceItemRepository repo;

    public ListCatalogueHandler(ServiceItemRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<ServiceItem> list(ServiceCategory category, Boolean activeOnly) {
        if (Boolean.TRUE.equals(activeOnly)) {
            return repo.findAllByCategoryAndActiveOrderBySortOrderAscNameEnAsc(category, true);
        }
        return repo.findAllByCategoryOrderBySortOrderAscNameEnAsc(category);
    }

    @Transactional(readOnly = true)
    public ServiceItem byId(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Service item not found: " + id));
    }
}
