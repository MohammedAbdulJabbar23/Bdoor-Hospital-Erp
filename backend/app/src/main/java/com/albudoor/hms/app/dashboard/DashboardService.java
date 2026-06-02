package com.albudoor.hms.app.dashboard;

import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.departmentservices.domain.DepartmentCaseStatus;
import com.albudoor.hms.departmentservices.infrastructure.DepartmentCaseRepository;
import com.albudoor.hms.emergency.domain.EmergencyCaseStatus;
import com.albudoor.hms.emergency.infrastructure.EmergencyBedRepository;
import com.albudoor.hms.emergency.infrastructure.EmergencyCaseRepository;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Computes the live dashboard summary by aggregating across bounded contexts.
 *
 * <p>Lives in the {@code app} composition root because it injects repositories from
 * several modules. Read-only: it never mutates state, just counts.
 */
@Service
public class DashboardService {

    /** Visit statuses that mean a department queue still has work to do. */
    private static final List<VisitStatus> NON_TERMINAL_VISIT_STATUSES = List.of(
            VisitStatus.CREATED,
            VisitStatus.AWAITING_PAYMENT,
            VisitStatus.IN_PROGRESS,
            VisitStatus.AWAITING_RESULTS,
            VisitStatus.AWAITING_FINAL_PAYMENT,
            VisitStatus.TREATMENT_FINISHED);

    /** Department-service cases that are still open (not closed/returned/cancelled). */
    private static final List<DepartmentCaseStatus> OPEN_CASE_STATUSES = List.of(
            DepartmentCaseStatus.NEW,
            DepartmentCaseStatus.AWAITING_PAYMENT,
            DepartmentCaseStatus.AWAITING_STUDY,
            DepartmentCaseStatus.FINDINGS_COMPLETE);

    /** Premature admissions whose bed is occupied (patient under care, not yet closed/cancelled). */
    private static final List<AdmissionStatus> ACTIVE_ADMISSION_STATUSES = List.of(
            AdmissionStatus.UNDER_CARE,
            AdmissionStatus.TREATMENT_FINISHED,
            AdmissionStatus.AWAITING_DISCHARGE_PAYMENT);

    /** Emergency cases whose bed is occupied (patient under treatment, not yet closed/cancelled). */
    private static final List<EmergencyCaseStatus> ACTIVE_CASE_STATUSES = List.of(
            EmergencyCaseStatus.UNDER_TREATMENT,
            EmergencyCaseStatus.TREATMENT_FINISHED,
            EmergencyCaseStatus.AWAITING_DISCHARGE_PAYMENT);

    private static final Duration EXPIRING_SOON_WINDOW = Duration.ofHours(2);

    private final VisitRepository visits;
    private final PaymentRepository payments;
    private final BedRepository prematureBeds;
    private final EmergencyBedRepository emergencyBeds;
    private final PrematureAdmissionRepository prematureAdmissions;
    private final EmergencyCaseRepository emergencyCases;
    private final DepartmentCaseRepository departmentCases;

    public DashboardService(
            VisitRepository visits,
            PaymentRepository payments,
            BedRepository prematureBeds,
            EmergencyBedRepository emergencyBeds,
            PrematureAdmissionRepository prematureAdmissions,
            EmergencyCaseRepository emergencyCases,
            DepartmentCaseRepository departmentCases) {
        this.visits = visits;
        this.payments = payments;
        this.prematureBeds = prematureBeds;
        this.emergencyBeds = emergencyBeds;
        this.prematureAdmissions = prematureAdmissions;
        this.emergencyCases = emergencyCases;
        this.departmentCases = departmentCases;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        ZoneId zone = ZoneId.systemDefault();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(zone);
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant expiringThreshold = now.plus(EXPIRING_SOON_WINDOW);

        long patientsToday = visits.countDistinctPatientsStartedBetween(dayStart, dayEnd);
        long pendingPayments = payments.countByStatus(PaymentStatus.PENDING);

        long bedsOccupied =
                prematureBeds.countByStatus(com.albudoor.hms.premature.domain.BedStatus.OCCUPIED)
                        + emergencyBeds.countByStatus(com.albudoor.hms.emergency.domain.BedStatus.OCCUPIED);
        long bedsTotal = prematureBeds.countByActiveTrue() + emergencyBeds.countByActiveTrue();

        long activeQueues = visits.countDistinctVisitTypesByStatusIn(NON_TERMINAL_VISIT_STATUSES);

        long labResultsAwaiting = departmentCases.countByStatusIn(OPEN_CASE_STATUSES);

        long bedsExpiringSoon =
                prematureAdmissions.countByStatusInAndStayExpiresAtBefore(
                        ACTIVE_ADMISSION_STATUSES, expiringThreshold)
                        + emergencyCases.countByStatusInAndStayExpiresAtBefore(
                        ACTIVE_CASE_STATUSES, expiringThreshold);

        return new DashboardSummaryResponse(
                patientsToday,
                pendingPayments,
                bedsOccupied,
                bedsTotal,
                activeQueues,
                pendingPayments,
                labResultsAwaiting,
                bedsExpiringSoon);
    }
}
