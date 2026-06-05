package com.albudoor.hms.pharmacy.summary;

import com.albudoor.hms.pharmacy.domain.DispenseStatus;
import com.albudoor.hms.pharmacy.infrastructure.PharmacyDispenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;

/**
 * Read-only pharmacy-queue reporting: per-status totals + a "dispensed today" count.
 *
 * <p>Mirrors the cashier/visit-queue summary approach — every number is a DB aggregate, never a
 * paging+summing pass — so the queue tiles are accurate even when the queue exceeds the listing cap.
 * "Today" is interpreted in the server's local zone, matching the dashboard, cashier and visit
 * summaries.
 */
@Service
public class DispenseSummaryService {

    private final PharmacyDispenseRepository repo;

    public DispenseSummaryService(PharmacyDispenseRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public DispenseSummaryResponse summary() {
        Map<DispenseStatus, Long> byStatus = new EnumMap<>(DispenseStatus.class);
        for (Object[] row : repo.countByStatusGrouped()) {
            byStatus.put((DispenseStatus) row[0], ((Number) row[1]).longValue());
        }

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        long dispensedToday = repo.countDispensedBetween(dayStart, dayEnd);

        return new DispenseSummaryResponse(byStatus, dispensedToday);
    }
}
