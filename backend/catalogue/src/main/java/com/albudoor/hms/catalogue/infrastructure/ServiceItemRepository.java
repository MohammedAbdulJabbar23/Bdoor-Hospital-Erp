package com.albudoor.hms.catalogue.infrastructure;

import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.catalogue.domain.ServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceItemRepository extends JpaRepository<ServiceItem, UUID> {

    boolean existsByCategoryAndCode(ServiceCategory category, String code);

    Optional<ServiceItem> findByCategoryAndCode(ServiceCategory category, String code);

    List<ServiceItem> findAllByCategoryOrderBySortOrderAscNameEnAsc(ServiceCategory category);

    List<ServiceItem> findAllByCategoryAndActiveOrderBySortOrderAscNameEnAsc(
            ServiceCategory category, boolean active);
}
