package com.albudoor.hms.emergency.orderworkup;

import com.albudoor.hms.emergency.domain.EmergencyCase;
import com.albudoor.hms.emergency.domain.EmergencyCaseStatus;
import com.albudoor.hms.emergency.infrastructure.EmergencyCaseRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import com.albudoor.hms.visitmanagement.forwardvisit.ForwardVisitCommand;
import com.albudoor.hms.visitmanagement.forwardvisit.ForwardVisitHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service("emergencyOrderWorkupHandler")
public class OrderWorkupHandler {

    private static final Set<VisitType> ORDERABLE = EnumSet.of(
            VisitType.LABORATORY, VisitType.RADIOLOGY, VisitType.ECO);

    private final EmergencyCaseRepository cases;
    private final ForwardVisitHandler forwardVisit;

    public OrderWorkupHandler(EmergencyCaseRepository cases, ForwardVisitHandler forwardVisit) {
        this.cases = cases;
        this.forwardVisit = forwardVisit;
    }

    @Transactional
    public Visit handle(UUID caseId, OrderWorkupCommand cmd) {
        if (!ORDERABLE.contains(cmd.targetType())) {
            throw new DomainException("INVALID_ORDER_TARGET",
                    "Can only order LABORATORY, RADIOLOGY or ECO; got " + cmd.targetType());
        }
        EmergencyCase c = cases.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Case not found: " + caseId));
        if (c.getStatus() != EmergencyCaseStatus.UNDER_TREATMENT) {
            throw new DomainException("CASE_NOT_ORDERABLE",
                    "Can only order while UNDER_TREATMENT (status=" + c.getStatus() + ")");
        }
        // Non-pausing forward: the case's visit stays IN_PROGRESS.
        return forwardVisit.handle(c.getVisitId(), new ForwardVisitCommand(cmd.targetType()), false).child();
    }
}
