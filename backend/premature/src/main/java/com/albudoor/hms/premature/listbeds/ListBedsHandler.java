package com.albudoor.hms.premature.listbeds;

import com.albudoor.hms.premature.api.BedResponse;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.domain.BedStatus;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListBedsHandler {

    private static final List<AdmissionStatus> ACTIVE = List.of(
            AdmissionStatus.AWAITING_ADMISSION_PAYMENT,
            AdmissionStatus.UNDER_CARE,
            AdmissionStatus.TREATMENT_FINISHED,
            AdmissionStatus.AWAITING_DISCHARGE_PAYMENT);

    private final BedRepository beds;
    private final PrematureAdmissionRepository admissions;

    public ListBedsHandler(BedRepository beds, PrematureAdmissionRepository admissions) {
        this.beds = beds;
        this.admissions = admissions;
    }

    @Transactional(readOnly = true)
    public List<BedResponse> list() {
        return beds.findAllByOrderByCodeAsc().stream().map(this::toResponse).toList();
    }

    private BedResponse toResponse(Bed bed) {
        BedResponse base = BedResponse.from(bed);
        if (bed.getStatus() == BedStatus.AVAILABLE) {
            return base;
        }
        return admissions.findByBedIdAndStatusIn(bed.getId(), ACTIVE)
                .map(a -> base.withOccupant(toOccupant(a)))
                .orElse(base);
    }

    private BedResponse.Occupant toOccupant(PrematureAdmission a) {
        return new BedResponse.Occupant(
                a.getId(), a.getVisitId(), a.getVisitDisplayId(),
                a.getPatientName(), a.getPatientMrn(),
                a.getStatus().name(), a.getStayExpiresAt());
    }
}
