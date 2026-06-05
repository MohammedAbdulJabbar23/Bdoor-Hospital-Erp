package com.albudoor.hms.visitmanagement.summary;

import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;

/**
 * Read-only visit-queue reporting: per-status totals + a "completed today" count.
 *
 * <p>Mirrors the cashier summary approach — every number is a DB aggregate, never a
 * paging+summing pass — so the queue tiles are accurate even when the queue exceeds the
 * 100-row listing cap. "Today" is interpreted in the server's local zone, matching the
 * dashboard and cashier summaries.
 */
@Service
public class VisitSummaryService {

    private final VisitRepository visits;

    public VisitSummaryService(VisitRepository visits) {
        this.visits = visits;
    }

    @Transactional(readOnly = true)
    public VisitSummaryResponse summary(VisitType type) {
        Map<VisitStatus, Long> byStatus = new EnumMap<>(VisitStatus.class);
        for (Object[] row : visits.countByStatusGrouped(type)) {
            byStatus.put((VisitStatus) row[0], ((Number) row[1]).longValue());
        }

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        long completedToday = visits.countCompletedBetween(dayStart, dayEnd, type);

        return new VisitSummaryResponse(byStatus, completedToday);
    }
}
