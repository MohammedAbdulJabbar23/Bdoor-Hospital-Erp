package com.albudoor.hms.emergency.listbeds;

import com.albudoor.hms.emergency.api.BedResponse;
import com.albudoor.hms.emergency.infrastructure.EmergencyBedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListBedsHandler {

    private final EmergencyBedRepository beds;

    public ListBedsHandler(EmergencyBedRepository beds) {
        this.beds = beds;
    }

    @Transactional(readOnly = true)
    public List<BedResponse> list() {
        return beds.findAllByOrderByCodeAsc().stream().map(BedResponse::from).toList();
    }
}
