package com.albudoor.hms.premature.recordtour;

import com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.domain.PrematureTour;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.premature.infrastructure.PrematureTourRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RecordTourHandler {

    private final PrematureTourRepository tours;
    private final PrematureAdmissionRepository admissions;

    public RecordTourHandler(PrematureTourRepository tours, PrematureAdmissionRepository admissions) {
        this.tours = tours;
        this.admissions = admissions;
    }

    @Transactional
    public PrematureTour handle(UUID admissionId, RecordTourCommand cmd) {
        if (!admissions.existsById(admissionId)) {
            throw new NotFoundException("Admission not found: " + admissionId);
        }
        PrematureTour tour = PrematureTour.record(admissionId, cmd.tourType(), currentUserId(), cmd.toVitals());
        return tours.save(tour);
    }

    private static UUID currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof HmsUserPrincipal p) return p.userId();
        return null;
    }
}
