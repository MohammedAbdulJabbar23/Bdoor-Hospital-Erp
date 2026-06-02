package com.albudoor.hms.cashier.summary;

import com.albudoor.hms.cashier.domain.PaymentMethod;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only cashier reporting: live KPIs and close-of-day reconciliation.
 *
 * <p>All numbers come from DB {@code count}/{@code sum}/{@code group by} queries on
 * {@link PaymentRepository}, never by loading pages and summing in memory, so they remain
 * correct regardless of queue size. "Today" / the reconciliation date is interpreted in the
 * server's local zone, matching the dashboard summary.
 */
@Service
public class PaymentSummaryService {

    private final PaymentRepository repo;

    public PaymentSummaryService(PaymentRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public PaymentSummaryResponse summary() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();

        long pendingCount = repo.countByStatus(PaymentStatus.PENDING);
        BigDecimal pendingTotal = repo.sumTotalDueByStatus(PaymentStatus.PENDING);

        // receivedToday / approvedTodayCount EXCLUDE VIP-bypass — no money was collected for those.
        BigDecimal receivedToday = repo.sumApprovedBetween(dayStart, dayEnd, true);
        long approvedTodayCount = repo.countApprovedBetween(dayStart, dayEnd, true);

        Instant oldestPendingAt = repo.minCreatedAtByStatus(PaymentStatus.PENDING);

        Map<PaymentStage, Long> pendingByStage = new EnumMap<>(PaymentStage.class);
        for (Object[] row : repo.countByStatusGroupedByStage(PaymentStatus.PENDING)) {
            pendingByStage.put((PaymentStage) row[0], ((Number) row[1]).longValue());
        }

        return new PaymentSummaryResponse(
                pendingCount,
                nz(pendingTotal),
                nz(receivedToday),
                approvedTodayCount,
                oldestPendingAt,
                pendingByStage);
    }

    @Transactional(readOnly = true)
    public ReconciliationResponse reconciliation(LocalDate date) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate day = date != null ? date : LocalDate.now(zone);
        Instant dayStart = day.atStartOfDay(zone).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();

        List<ReconciliationResponse.Bucket> byMethod = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        long grandCount = 0;
        ReconciliationResponse.Total vip = ReconciliationResponse.Total.empty();

        for (Object[] row : repo.reconcileByMethodBetween(dayStart, dayEnd)) {
            PaymentMethod method = (PaymentMethod) row[0];
            BigDecimal total = nz((BigDecimal) row[1]);
            long count = ((Number) row[2]).longValue();
            byMethod.add(ReconciliationResponse.bucket(method, total, count));
            grandTotal = grandTotal.add(total);
            grandCount += count;
            if (method == PaymentMethod.VIP_BYPASS) {
                vip = new ReconciliationResponse.Total(total, count);
            }
        }

        List<ReconciliationResponse.Bucket> byStage = new ArrayList<>();
        for (Object[] row : repo.reconcileByStageBetween(dayStart, dayEnd)) {
            PaymentStage stage = (PaymentStage) row[0];
            BigDecimal total = nz((BigDecimal) row[1]);
            long count = ((Number) row[2]).longValue();
            byStage.add(ReconciliationResponse.bucket(stage, total, count));
        }

        ReconciliationResponse.Total grand = new ReconciliationResponse.Total(grandTotal, grandCount);
        ReconciliationResponse.Total cash = new ReconciliationResponse.Total(
                grandTotal.subtract(vip.total()), grandCount - vip.count());

        return new ReconciliationResponse(day, byMethod, byStage, grand, vip, cash);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
