package com.albudoor.hms.clinicalcase.upsertexam;

import com.albudoor.hms.clinicalcase.domain.DiagnosisEntry;
import com.albudoor.hms.clinicalcase.domain.DoctorExam;
import com.albudoor.hms.clinicalcase.domain.PrescriptionEntry;
import com.albudoor.hms.clinicalcase.domain.Vitals;
import com.albudoor.hms.clinicalcase.infrastructure.DoctorExamRepository;
import com.albudoor.hms.doctorappointment.domain.Doctor;
import com.albudoor.hms.doctorappointment.infrastructure.DoctorRepository;
import com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class UpsertExamHandler {

    private final DoctorExamRepository exams;
    private final VisitRepository visits;
    private final DoctorRepository doctors;

    public UpsertExamHandler(DoctorExamRepository exams, VisitRepository visits, DoctorRepository doctors) {
        this.exams = exams;
        this.visits = visits;
        this.doctors = doctors;
    }

    @Transactional
    public DoctorExam handle(UpsertExamCommand cmd) {
        Visit visit = visits.findById(cmd.visitId())
                .orElseThrow(() -> new NotFoundException("Visit not found: " + cmd.visitId()));

        if (visit.getVisitType() != VisitType.DOCTOR_APPOINTMENT
                && visit.getVisitType() != VisitType.EMERGENCY
                && visit.getVisitType() != VisitType.PREMATURE) {
            throw new DomainException("VISIT_NOT_CLINICAL",
                    "Doctor exam is only valid on clinical visit types (got " + visit.getVisitType() + ")");
        }

        DoctorExam exam = exams.findByVisitId(visit.getId()).orElse(null);
        if (exam == null) {
            UUID doctorId = visit.getAssignedDoctorId();
            String doctorName;
            if (doctorId != null) {
                Doctor doc = doctors.findById(doctorId).orElse(null);
                doctorName = doc != null ? doc.getFullName() : "Unassigned";
            } else {
                // Fall back to current user's name (when a doctor opens an exam without
                // a prior assignment, e.g. walk-in to Emergency).
                doctorId = currentUserId();
                doctorName = currentUserName();
                if (doctorId == null) {
                    throw new DomainException("NO_DOCTOR",
                            "No doctor assigned to visit and no current user; cannot start exam");
                }
            }
            exam = DoctorExam.start(
                    visit.getId(), visit.getVisitDisplayId(),
                    visit.getPatientId(), visit.getPatientMrn(), visit.getPatientName(),
                    doctorId, doctorName);
            exam = exams.save(exam);
        }

        Vitals vitals = mapVitals(cmd.vitals());
        List<DiagnosisEntry> diagnoses = new ArrayList<>();
        if (cmd.diagnoses() != null) {
            for (UpsertExamCommand.Diagnosis d : cmd.diagnoses()) {
                diagnoses.add(new DiagnosisEntry(d.code(), d.description(), d.primary(), d.notes()));
            }
        }
        List<PrescriptionEntry> prescriptions = new ArrayList<>();
        if (cmd.prescriptions() != null) {
            for (UpsertExamCommand.Prescription p : cmd.prescriptions()) {
                prescriptions.add(new PrescriptionEntry(
                        p.drugServiceItemId(), p.drugCode(), p.drugName(), p.strength(),
                        p.dose(), p.frequency(), p.duration(), p.route(), p.notes()));
            }
        }
        exam.replace(vitals,
                cmd.chiefComplaint(), cmd.historyOfPresentIllness(),
                cmd.examinationNotes(), cmd.plan(), cmd.referralInstructions(),
                diagnoses, prescriptions);
        return exam;
    }

    private static Vitals mapVitals(UpsertExamCommand.VitalsPart v) {
        if (v == null) return Vitals.empty();
        return new Vitals(
                v.systolicBp(), v.diastolicBp(),
                v.heartRate(), v.respiratoryRate(),
                v.temperatureC(), v.oxygenSaturation(),
                v.weightKg(), v.heightCm(),
                v.notes()
        );
    }

    private static UUID currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof HmsUserPrincipal p) ? p.userId() : null;
    }

    private static String currentUserName() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof HmsUserPrincipal p) ? p.fullName() : null;
    }
}
