package com.albudoor.hms.emergency.listbeds;

import com.albudoor.hms.emergency.api.BedResponse;
import com.albudoor.hms.emergency.domain.BedStatus;
import com.albudoor.hms.emergency.domain.EmergencyBed;
import com.albudoor.hms.emergency.domain.EmergencyCase;
import com.albudoor.hms.emergency.domain.EmergencyCaseStatus;
import com.albudoor.hms.emergency.infrastructure.EmergencyBedRepository;
import com.albudoor.hms.emergency.infrastructure.EmergencyCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service("emergencyListBedsHandler")
public class ListBedsHandler {

    private static final List<EmergencyCaseStatus> ACTIVE = List.of(
            EmergencyCaseStatus.AWAITING_INITIAL_PAYMENT,
            EmergencyCaseStatus.UNDER_TREATMENT,
            EmergencyCaseStatus.TREATMENT_FINISHED,
            EmergencyCaseStatus.AWAITING_DISCHARGE_PAYMENT);

    private final EmergencyBedRepository beds;
    private final EmergencyCaseRepository cases;

    public ListBedsHandler(EmergencyBedRepository beds, EmergencyCaseRepository cases) {
        this.beds = beds;
        this.cases = cases;
    }

    @Transactional(readOnly = true)
    public List<BedResponse> list() {
        return beds.findAllByOrderByCodeAsc().stream().map(this::toResponse).toList();
    }

    private BedResponse toResponse(EmergencyBed bed) {
        BedResponse base = BedResponse.from(bed);
        if (bed.getStatus() == BedStatus.AVAILABLE) {
            return base;
        }
        return cases.findByBedIdAndStatusIn(bed.getId(), ACTIVE)
                .map(c -> base.withOccupant(toOccupant(c)))
                .orElse(base);
    }

    private BedResponse.Occupant toOccupant(EmergencyCase c) {
        return new BedResponse.Occupant(
                c.getId(), c.getVisitId(), c.getVisitDisplayId(),
                c.getPatientName(), c.getPatientMrn(),
                c.getStatus().name(), c.getStayExpiresAt());
    }
}
