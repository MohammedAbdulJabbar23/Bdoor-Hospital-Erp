package com.albudoor.hms.pharmacy.bridge;

import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.catalogue.domain.ServiceItem;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.clinicalcase.domain.DoctorExam;
import com.albudoor.hms.clinicalcase.domain.ExamFinalizedEvent;
import com.albudoor.hms.clinicalcase.domain.PrescriptionEntry;
import com.albudoor.hms.clinicalcase.infrastructure.DoctorExamRepository;
import com.albudoor.hms.pharmacy.domain.DispenseLine;
import com.albudoor.hms.pharmacy.domain.PharmacyDispense;
import com.albudoor.hms.pharmacy.infrastructure.DispenseIdGenerator;
import com.albudoor.hms.pharmacy.infrastructure.PharmacyDispenseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * When a doctor finalizes an exam, snapshot its prescriptions into a {@link PharmacyDispense}
 * so the pharmacy queue can pick it up. Idempotent — re-firing for the same exam is a no-op
 * (the dispense table has a unique constraint on {@code exam_id}).
 *
 * <p>Lines that resolve to an active catalogue drug get priced; free-text Rx entries (no
 * {@code drugServiceItemId} or no fee) are kept on the dispense for record-keeping but
 * don't contribute to the billable total.
 *
 * <p>Skipped: an exam with no prescriptions at all — there's nothing to dispense.
 */
@Component
public class ExamFinalizedToDispenseBridge {

    private static final Logger log = LoggerFactory.getLogger(ExamFinalizedToDispenseBridge.class);

    private final DoctorExamRepository exams;
    private final ServiceItemRepository services;
    private final PharmacyDispenseRepository dispenses;
    private final DispenseIdGenerator idGenerator;
    private final ApplicationEventPublisher events;

    public ExamFinalizedToDispenseBridge(
            DoctorExamRepository exams,
            ServiceItemRepository services,
            PharmacyDispenseRepository dispenses,
            DispenseIdGenerator idGenerator,
            ApplicationEventPublisher events
    ) {
        this.exams = exams;
        this.services = services;
        this.dispenses = dispenses;
        this.idGenerator = idGenerator;
        this.events = events;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFinalized(ExamFinalizedEvent event) {
        if (event.prescriptionCount() == 0) return;

        // Idempotency: a re-fire (e.g. via retry) must not create a duplicate dispense.
        if (dispenses.findByExamId(event.examId()).isPresent()) {
            log.debug("Dispense already exists for exam {}; skipping", event.examId());
            return;
        }

        DoctorExam exam = exams.findById(event.examId()).orElse(null);
        if (exam == null) {
            log.warn("ExamFinalized for unknown exam {}", event.examId());
            return;
        }

        List<DispenseLine> lines = new ArrayList<>();
        for (PrescriptionEntry rx : exam.getPrescriptions()) {
            lines.add(toDispenseLine(rx));
        }
        if (lines.isEmpty()) return;

        PharmacyDispense d = PharmacyDispense.fromExam(
                idGenerator.next(),
                exam.getId(),
                exam.getVisitId(), exam.getVisitDisplayId(),
                exam.getPatientId(), exam.getPatientMrn(), exam.getPatientName(),
                exam.getDoctorId(), exam.getDoctorName(),
                lines
        );
        PharmacyDispense saved = dispenses.save(d);
        // Spring Data JPA merge() returns a different managed instance for entities with
        // pre-assigned ids; events were registered on the source reference.
        d.pullDomainEvents().forEach(events::publishEvent);
        log.info("Pharmacy dispense {} created for exam {} ({} lines, {} billable)",
                saved.getDispenseDisplayId(), exam.getId(),
                saved.getLines().size(),
                saved.getLines().stream().filter(DispenseLine::isBillable).count());
    }

    private DispenseLine toDispenseLine(PrescriptionEntry rx) {
        // Price the line only if the doctor picked a catalogue item that is a DRUG, still
        // active, and has a fee. Items in any other category (LAB/IMAGING/EMERGENCY/etc.) are
        // NOT pharmacy-dispensable drugs and must not be billed as such — they fall through to
        // an informational (non-billable) line keyed on the doctor's free-text drug name.
        if (rx.getDrugServiceItemId() != null) {
            ServiceItem item = services.findById(rx.getDrugServiceItemId()).orElse(null);
            if (item != null && item.getCategory() == ServiceCategory.DRUG
                    && item.isActive() && item.getFee() != null) {
                int qty = parseQuantityFromDuration(rx.getDuration(), rx.getFrequency());
                return DispenseLine.billable(
                        item.getId(), item.getCode(), item.getNameEn(),
                        rx.getStrength(), rx.getDose(), rx.getFrequency(), rx.getDuration(),
                        rx.getRoute(), rx.getNotes(),
                        item.getFee(), qty
                );
            } else if (item != null && item.getCategory() != ServiceCategory.DRUG) {
                log.warn("Prescription referenced non-DRUG catalogue item {} (category {}); "
                                + "treating as informational, not billing as a dispensed drug",
                        item.getId(), item.getCategory());
            }
        }
        return DispenseLine.informational(
                rx.getDrugName(), rx.getStrength(), rx.getDose(), rx.getFrequency(),
                rx.getDuration(), rx.getRoute(), rx.getNotes()
        );
    }

    /**
     * Best-effort quantity inference from free-text "duration" (e.g. "5 days", "2 weeks").
     * Defaults to 1 if nothing parses. Pharmacist can adjust later via cancel/re-charge if needed.
     */
    private int parseQuantityFromDuration(String duration, String frequency) {
        if (duration == null) return 1;
        String d = duration.trim().toLowerCase();
        try {
            // Pull leading integer.
            StringBuilder num = new StringBuilder();
            for (char c : d.toCharArray()) {
                if (Character.isDigit(c)) num.append(c);
                else if (num.length() > 0) break;
            }
            if (num.length() == 0) return 1;
            int days = Integer.parseInt(num.toString());
            if (d.contains("week")) days *= 7;
            else if (d.contains("month")) days *= 30;
            return Math.max(1, days);
        } catch (NumberFormatException ex) {
            return 1;
        }
    }
}
