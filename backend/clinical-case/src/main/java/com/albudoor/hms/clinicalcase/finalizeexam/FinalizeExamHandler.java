package com.albudoor.hms.clinicalcase.finalizeexam;

import com.albudoor.hms.clinicalcase.domain.DoctorExam;
import com.albudoor.hms.clinicalcase.infrastructure.DoctorExamRepository;
import com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Locks the doctor's exam. If the visit isn't blocked on outstanding forwarded results,
 * the visit is also moved to COMPLETED. Otherwise the visit stays at IN_PROGRESS /
 * AWAITING_RESULTS and the exam can be reopened by admin if the doctor needs to add
 * findings later.
 */
@Service
public class FinalizeExamHandler {

    private final DoctorExamRepository exams;
    private final VisitRepository visits;
    private final ApplicationEventPublisher events;

    public FinalizeExamHandler(
            DoctorExamRepository exams,
            VisitRepository visits,
            ApplicationEventPublisher events
    ) {
        this.exams = exams;
        this.visits = visits;
        this.events = events;
    }

    @Transactional
    public DoctorExam handle(UUID examId) {
        DoctorExam exam = exams.findById(examId)
                .orElseThrow(() -> new NotFoundException("Exam not found: " + examId));

        // Payment gate: the visit must be an in-progress consult (IN_PROGRESS) — or still
        // waiting on forwarded results (AWAITING_RESULTS) — to finalize the exam. This blocks
        // finalizing on an unpaid (CREATED/AWAITING_PAYMENT) or already-closed visit.
        Visit visit = visits.findById(exam.getVisitId()).orElse(null);
        if (visit != null) {
            visit.requireExamRecordable();
        }

        exam.finalize(currentUserId());
        exam.pullDomainEvents().forEach(events::publishEvent);

        // Close the visit when the consult is done and not blocked on forwarded results.
        // IN_PROGRESS -> COMPLETED is always a valid transition, so a failure here is a real
        // error and must surface (no longer silently swallowed). When AWAITING_RESULTS we leave
        // the visit open; finalizing the returned results elsewhere resumes/closes it.
        if (visit != null && visit.getStatus() == VisitStatus.IN_PROGRESS) {
            visit.transitionTo(VisitStatus.COMPLETED);
            visit.pullDomainEvents().forEach(events::publishEvent);
        }
        return exam;
    }

    private static UUID currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof HmsUserPrincipal p) ? p.userId() : null;
    }
}
