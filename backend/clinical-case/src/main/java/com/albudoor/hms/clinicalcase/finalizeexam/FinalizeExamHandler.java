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

        exam.finalize(currentUserId());
        exam.pullDomainEvents().forEach(events::publishEvent);

        // Try to close the visit if it can be closed.
        Visit visit = visits.findById(exam.getVisitId()).orElse(null);
        if (visit != null && visit.getStatus() == VisitStatus.IN_PROGRESS) {
            try {
                visit.transitionTo(VisitStatus.COMPLETED);
                visit.pullDomainEvents().forEach(events::publishEvent);
            } catch (RuntimeException ignored) {
                // Visit can't move to COMPLETED — leave it; doctor can finalize again later.
            }
        }
        return exam;
    }

    private static UUID currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof HmsUserPrincipal p) ? p.userId() : null;
    }
}
