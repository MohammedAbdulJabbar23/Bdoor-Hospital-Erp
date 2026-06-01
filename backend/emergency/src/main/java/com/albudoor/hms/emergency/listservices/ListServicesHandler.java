package com.albudoor.hms.emergency.listservices;

import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.emergency.api.EmergencyServiceResponse;
import com.albudoor.hms.emergency.domain.EmergencyServiceCodes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service("emergencyListServicesHandler")
public class ListServicesHandler {
    private final ServiceItemRepository catalogue;
    public ListServicesHandler(ServiceItemRepository catalogue) { this.catalogue = catalogue; }

    @Transactional(readOnly = true)
    public List<EmergencyServiceResponse> list() {
        return catalogue.findAllByCategoryAndActiveOrderBySortOrderAscNameEnAsc(ServiceCategory.EMERGENCY, true)
                .stream().filter(s -> s.getForwardTo() == null)
                .filter(s -> !EmergencyServiceCodes.DISCHARGE.equals(s.getCode()))
                .map(EmergencyServiceResponse::from).toList();
    }
}
