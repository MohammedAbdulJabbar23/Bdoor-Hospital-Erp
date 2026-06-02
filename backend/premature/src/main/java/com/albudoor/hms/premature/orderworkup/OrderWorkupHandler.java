package com.albudoor.hms.premature.orderworkup;

import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import com.albudoor.hms.visitmanagement.forwardvisit.ForwardVisitCommand;
import com.albudoor.hms.visitmanagement.forwardvisit.ForwardVisitHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderWorkupHandler {

    private static final Set<VisitType> ORDERABLE = EnumSet.of(
            VisitType.LABORATORY, VisitType.RADIOLOGY, VisitType.ECO);

    private final PrematureAdmissionRepository admissions;
    private final ForwardVisitHandler forwardVisit;

    public OrderWorkupHandler(PrematureAdmissionRepository admissions, ForwardVisitHandler forwardVisit) {
        this.admissions = admissions;
        this.forwardVisit = forwardVisit;
    }

    @Transactional
    public Visit handle(UUID admissionId, OrderWorkupCommand cmd) {
        if (!ORDERABLE.contains(cmd.targetType())) {
            throw new DomainException("INVALID_ORDER_TARGET",
                    "Can only order LABORATORY, RADIOLOGY or ECO; got " + cmd.targetType());
        }
        PrematureAdmission a = admissions.findById(admissionId)
                .orElseThrow(() -> new NotFoundException("Admission not found: " + admissionId));
        if (a.getStatus() != AdmissionStatus.UNDER_CARE) {
            throw new DomainException("ADMISSION_NOT_ORDERABLE",
                    "Can only order while UNDER_CARE (status=" + a.getStatus() + ")");
        }
        // Non-pausing forward: the admission's visit stays IN_PROGRESS.
        return forwardVisit.handle(a.getVisitId(), new ForwardVisitCommand(cmd.targetType()), false).child();
    }
}
