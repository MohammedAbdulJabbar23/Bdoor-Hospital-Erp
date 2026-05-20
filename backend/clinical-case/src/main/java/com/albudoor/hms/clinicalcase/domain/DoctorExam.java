package com.albudoor.hms.clinicalcase.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The doctor's exam record for one Visit. One DoctorExam per Visit (1:1).
 *
 * <p>Lifecycle: created on first edit (status = DRAFT), every PUT replaces all fields.
 * Once {@link #finalize(UUID)} runs, the record is locked — further edits return 422.
 *
 * <p>The patient identity (mrn + name) and doctor name are snapshotted at creation so
 * historical records remain intact when the patient is renamed or the doctor's profile
 * changes.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "doctor_exam",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_doctor_exam_visit", columnNames = "visit_id"),
        indexes = {
                @Index(name = "idx_doctor_exam_patient", columnList = "patient_id"),
                @Index(name = "idx_doctor_exam_doctor",  columnList = "doctor_id"),
                @Index(name = "idx_doctor_exam_status",  columnList = "status")
        }
)
public class DoctorExam extends AggregateRoot {

    @Id
    private UUID id;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "visit_display_id", nullable = false, length = 30)
    private String visitDisplayId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "patient_mrn", nullable = false, length = 30)
    private String patientMrn;

    @Column(name = "patient_name", nullable = false, length = 300)
    private String patientName;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "doctor_name", nullable = false, length = 200)
    private String doctorName;

    @Embedded
    private Vitals vitals;

    @Column(name = "chief_complaint", length = 1000)
    private String chiefComplaint;

    @Column(name = "history_of_present_illness", length = 4000)
    private String historyOfPresentIllness;

    @Column(name = "examination_notes", length = 4000)
    private String examinationNotes;

    @Column(name = "plan", length = 4000)
    private String plan;

    @Column(name = "referral_instructions", length = 1000)
    private String referralInstructions;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "doctor_exam_diagnosis",
            joinColumns = @JoinColumn(name = "exam_id")
    )
    @OrderColumn(name = "line_no")
    private List<DiagnosisEntry> diagnoses = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "doctor_exam_prescription",
            joinColumns = @JoinColumn(name = "exam_id")
    )
    @OrderColumn(name = "line_no")
    private List<PrescriptionEntry> prescriptions = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExamStatus status;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "finalized_by")
    private UUID finalizedBy;

    public static DoctorExam start(
            UUID visitId, String visitDisplayId,
            UUID patientId, String patientMrn, String patientName,
            UUID doctorId, String doctorName
    ) {
        DoctorExam e = new DoctorExam();
        e.id = UUID.randomUUID();
        e.visitId = visitId;
        e.visitDisplayId = visitDisplayId;
        e.patientId = patientId;
        e.patientMrn = patientMrn;
        e.patientName = patientName;
        e.doctorId = doctorId;
        e.doctorName = doctorName;
        e.vitals = Vitals.empty();
        e.status = ExamStatus.DRAFT;
        return e;
    }

    public void replace(
            Vitals vitals,
            String chiefComplaint, String historyOfPresentIllness,
            String examinationNotes, String plan, String referralInstructions,
            List<DiagnosisEntry> diagnoses, List<PrescriptionEntry> prescriptions
    ) {
        ensureMutable();
        this.vitals = vitals != null ? vitals : Vitals.empty();
        this.chiefComplaint = trimOrNull(chiefComplaint);
        this.historyOfPresentIllness = trimOrNull(historyOfPresentIllness);
        this.examinationNotes = trimOrNull(examinationNotes);
        this.plan = trimOrNull(plan);
        this.referralInstructions = trimOrNull(referralInstructions);
        // Ensure exactly one primary diagnosis — first one wins if multiple flagged primary.
        boolean primarySeen = false;
        List<DiagnosisEntry> normalised = new ArrayList<>();
        if (diagnoses != null) {
            for (DiagnosisEntry d : diagnoses) {
                boolean isPrim = d.isPrimary() && !primarySeen;
                if (isPrim) primarySeen = true;
                normalised.add(new DiagnosisEntry(d.getCode(), d.getDescription(), isPrim, d.getNotes()));
            }
        }
        this.diagnoses.clear();
        this.diagnoses.addAll(normalised);
        this.prescriptions.clear();
        if (prescriptions != null) this.prescriptions.addAll(prescriptions);
    }

    public void finalize(UUID userId) {
        if (status == ExamStatus.FINALIZED) {
            throw new DomainException("EXAM_ALREADY_FINALIZED",
                    "Exam is already finalized; reopen via admin to edit");
        }
        if (diagnoses.isEmpty() && (chiefComplaint == null || chiefComplaint.isBlank())) {
            throw new DomainException("EXAM_INSUFFICIENT",
                    "Finalize requires at least a chief complaint or one diagnosis");
        }
        this.status = ExamStatus.FINALIZED;
        this.finalizedAt = Instant.now();
        this.finalizedBy = userId;
        registerEvent(ExamFinalizedEvent.of(this));
    }

    public void reopen() {
        if (status != ExamStatus.FINALIZED) return;
        this.status = ExamStatus.DRAFT;
        this.finalizedAt = null;
        this.finalizedBy = null;
    }

    public boolean isFinalized() {
        return status == ExamStatus.FINALIZED;
    }

    private void ensureMutable() {
        if (status == ExamStatus.FINALIZED) {
            throw new DomainException("EXAM_FINALIZED",
                    "Cannot edit a finalized exam; reopen via admin first");
        }
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
