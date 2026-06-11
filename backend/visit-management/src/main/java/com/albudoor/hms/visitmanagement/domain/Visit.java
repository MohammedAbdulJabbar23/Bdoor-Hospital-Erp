package com.albudoor.hms.visitmanagement.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * The Visit aggregate — one arrival event, one workflow.
 *
 * <p>Forwarded sub-visits carry a {@code parentVisitId} and an {@code originatingType}.
 * The patient's name and MRN are snapshotted at creation so queue/listings don't have
 * to join the patient registry on every read; if a patient is renamed later we accept
 * the snapshot (it's the value at the time of the visit).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "visit",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_visit_display_id", columnNames = "visit_display_id"),
        indexes = {
                @Index(name = "idx_visit_patient", columnList = "patient_id"),
                @Index(name = "idx_visit_type_status", columnList = "visit_type, status"),
                @Index(name = "idx_visit_parent", columnList = "parent_visit_id")
        }
)
public class Visit extends AggregateRoot {

    private static final Logger log = LoggerFactory.getLogger(Visit.class);

    @Id
    private UUID id;

    @Column(name = "visit_display_id", nullable = false, length = 30)
    private String visitDisplayId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "patient_mrn", nullable = false, length = 30)
    private String patientMrn;

    @Column(name = "patient_name", nullable = false, length = 300)
    private String patientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", nullable = false, length = 30)
    private VisitType visitType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VisitOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VisitStatus status;

    @Column(name = "parent_visit_id")
    private UUID parentVisitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "originating_type", length = 30)
    private VisitType originatingType;

    @Column(name = "assigned_doctor_id")
    private UUID assignedDoctorId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "closure_reason", length = 500)
    private String closureReason;

    @Column(name = "results_summary", length = 2000)
    private String resultsSummary;

    /**
     * When the {@code resultsSummary} was last (re)set by a returning forwarded child. Drives the
     * Topbar notification bell's "results received within the last 24h" filter on the ORIGINATING
     * visit — the parent's {@code endedAt} stays null while it is still in progress, so it cannot
     * be used for that purpose.
     */
    @Column(name = "results_last_updated_at")
    private Instant resultsLastUpdatedAt;

    /**
     * Free-text note captured when this visit was created as a forward/order — i.e. why the
     * patient was sent to this department. Null for direct visits. Shown to the receiving
     * department and on the originating case's order list.
     */
    @Column(name = "referral_note", length = 2000)
    private String referralNote;

    public static Visit createDirect(
            String visitDisplayId,
            UUID patientId,
            String patientMrn,
            String patientName,
            VisitType type,
            VisitOrigin origin,
            UUID assignedDoctorId
    ) {
        if (origin == VisitOrigin.FORWARDED) {
            throw new DomainException("INVALID_ORIGIN",
                    "Use createForwarded(...) for forwarded sub-visits");
        }
        Visit v = baseFields(visitDisplayId, patientId, patientMrn, patientName, type, origin);
        v.assignedDoctorId = assignedDoctorId;
        v.registerEvent(VisitCreatedEvent.of(v));
        return v;
    }

    public static Visit createForwarded(
            String visitDisplayId,
            UUID patientId,
            String patientMrn,
            String patientName,
            VisitType targetType,
            UUID parentVisitId,
            VisitType originatingType,
            String referralNote
    ) {
        if (parentVisitId == null) {
            throw new DomainException("PARENT_REQUIRED", "parentVisitId is required for forwarded visits");
        }
        if (originatingType == null) {
            throw new DomainException("ORIGINATING_TYPE_REQUIRED",
                    "originatingType is required for forwarded visits");
        }
        Visit v = baseFields(visitDisplayId, patientId, patientMrn, patientName,
                targetType, VisitOrigin.FORWARDED);
        v.parentVisitId = parentVisitId;
        v.originatingType = originatingType;
        v.referralNote = (referralNote == null || referralNote.isBlank()) ? null : referralNote.trim();
        v.registerEvent(VisitCreatedEvent.of(v));
        return v;
    }

    private static Visit baseFields(
            String visitDisplayId, UUID patientId, String patientMrn, String patientName,
            VisitType type, VisitOrigin origin
    ) {
        if (visitDisplayId == null || visitDisplayId.isBlank()) {
            throw new DomainException("VISIT_ID_REQUIRED", "visit display id is required");
        }
        if (patientId == null) {
            throw new DomainException("PATIENT_REQUIRED", "patientId is required");
        }
        if (patientMrn == null || patientMrn.isBlank()) {
            throw new DomainException("PATIENT_MRN_REQUIRED", "patientMrn is required");
        }
        if (patientName == null || patientName.isBlank()) {
            throw new DomainException("PATIENT_NAME_REQUIRED", "patientName is required");
        }
        if (type == null || origin == null) {
            throw new DomainException("VISIT_TYPE_OR_ORIGIN_REQUIRED",
                    "visit type and origin are required");
        }
        Visit v = new Visit();
        v.id = UUID.randomUUID();
        v.visitDisplayId = visitDisplayId;
        v.patientId = patientId;
        v.patientMrn = patientMrn;
        v.patientName = patientName;
        v.visitType = type;
        v.origin = origin;
        v.status = VisitStatus.CREATED;
        v.startedAt = Instant.now();
        return v;
    }

    /**
     * Drive the status state machine. Throws {@link DomainException} on illegal transitions.
     * Emits a {@link VisitStatusChangedEvent} so the cashier/notification modules can react.
     */
    public void transitionTo(VisitStatus target) {
        if (target == null) {
            throw new DomainException("STATUS_REQUIRED", "Target status is required");
        }
        if (this.status == target) return;
        if (!this.status.canTransitionTo(target)) {
            throw new DomainException("INVALID_VISIT_TRANSITION",
                    "Cannot transition visit from " + this.status + " to " + target);
        }
        VisitStatus previous = this.status;
        this.status = target;
        if (target.isTerminal() && this.endedAt == null) {
            this.endedAt = Instant.now();
        }
        registerEvent(VisitStatusChangedEvent.of(this.id, previous, target));
    }

    public void cancel(String reason) {
        if (this.status.isTerminal()) {
            throw new DomainException("VISIT_TERMINAL", "Cannot cancel a terminal visit");
        }
        this.closureReason = reason;
        transitionTo(VisitStatus.CANCELLED);
    }

    /**
     * Guards the clinical-exam workflow: a doctor may only record/finalize an exam once the
     * consultation payment is approved (visit IN_PROGRESS), or while the consult is paused on
     * forwarded results (AWAITING_RESULTS). Recording against a CREATED/AWAITING_PAYMENT visit
     * would bypass the cashier; recording against a closed (COMPLETED/CANCELLED) visit is invalid.
     */
    public void requireExamRecordable() {
        if (this.status != VisitStatus.IN_PROGRESS && this.status != VisitStatus.AWAITING_RESULTS) {
            throw new DomainException("VISIT_NOT_IN_PROGRESS",
                    "Exam can only be recorded once the visit is in progress");
        }
    }

    /** Called on the parent when a forwarded sub-visit is dispatched. */
    public void markAwaitingResults() {
        transitionTo(VisitStatus.AWAITING_RESULTS);
    }

    /**
     * Called on the parent when a forwarded sub-visit returns. Snapshots the results summary
     * and emits a {@link VisitReturnedEvent} so the originating department's queue updates.
     *
     * <p>If the parent has already reached a terminal state (COMPLETED/CANCELLED) the result
     * arrived after the parent was closed — a late lab/imaging return. We must NOT silently
     * overwrite the closed aggregate's {@code resultsSummary} or attempt a transition (which
     * would throw on the terminal state machine anyway). The child has already completed
     * normally via {@code completeForwardedWith}; here we just log and skip. No notification
     * system exists, so logging is the most we can do without mutating a closed visit.
     */
    public void receiveResultsFromChild(UUID childId, VisitType childType, String summary) {
        if (this.status.isTerminal()) {
            log.warn("Result returned to already-closed visit {} (status {}) from child {} ({}); "
                            + "skipping resultsSummary overwrite and transition",
                    this.id, this.status, childId, childType);
            return;
        }
        this.resultsSummary = summary;
        this.resultsLastUpdatedAt = Instant.now();
        if (this.status == VisitStatus.AWAITING_RESULTS) {
            // Doctor "pause-and-wait" pattern: resume the parent.
            transitionTo(VisitStatus.IN_PROGRESS);
        }
        // Bed-stay pattern: parent was never paused (stays IN_PROGRESS); just record + notify.
        registerEvent(VisitReturnedEvent.of(this.id, childId, childType, summary));
    }

    /** Called on a forwarded sub-visit when its work completes. */
    public void completeForwardedWith(String summary) {
        if (this.parentVisitId == null) {
            throw new DomainException("NOT_FORWARDED", "Visit has no parent to return to");
        }
        // The department visit must have started (payment approved → IN_PROGRESS) before its
        // results can be returned. Trying to return from CREATED/AWAITING_PAYMENT would otherwise
        // surface the generic INVALID_VISIT_TRANSITION; give the caller a clear, actionable error.
        if (this.status != VisitStatus.IN_PROGRESS) {
            throw new DomainException("SUBVISIT_NOT_IN_PROGRESS",
                    "Results can't be returned before the department visit is in progress "
                            + "(its payment must be approved first).");
        }
        this.resultsSummary = summary;
        // Stamp the child too: bed-stay order rows (premature/emergency OrderResponse.resultsAt)
        // read THIS visit's timestamp to render the "Results ready" state.
        this.resultsLastUpdatedAt = Instant.now();
        transitionTo(VisitStatus.COMPLETED);
    }

    public void assignDoctor(UUID doctorId) {
        this.assignedDoctorId = doctorId;
    }
}
