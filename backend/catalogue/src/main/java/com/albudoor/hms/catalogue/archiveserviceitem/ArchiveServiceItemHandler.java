package com.albudoor.hms.catalogue.archiveserviceitem;

import com.albudoor.hms.catalogue.domain.ServiceItem;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ArchiveServiceItemHandler {

    private final ServiceItemRepository repo;

    public ArchiveServiceItemHandler(ServiceItemRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public ServiceItem archive(UUID id) {
        ServiceItem item = load(id);
        item.archive();
        return item;
    }

    @Transactional
    public ServiceItem unarchive(UUID id) {
        ServiceItem item = load(id);
        item.unarchive();
        return item;
    }

    private ServiceItem load(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Service item not found: " + id));
    }
}
