package com.albudoor.hms.pharmacy.summary;

import com.albudoor.hms.pharmacy.domain.DispenseStatus;

import java.util.Map;

/**
 * Server-computed pharmacy-queue KPIs. All counts come from DB {@code count}/{@code group by}
 * queries, never by paging+summing client-side, so the queue tiles stay correct regardless of how
 * many dispenses exist (the row listing remains paginated and capped, but these totals are not).
 *
 * @param byStatus        per-{@link DispenseStatus} total counts (only non-zero statuses need be
 *                        present; consumers default missing statuses to 0)
 * @param dispensedToday  number of dispenses handed over today (status DISPENSED, by {@code givenAt})
 *                        — the "Dispensed today" tile counts these, not every DISPENSED dispense ever
 */
public record DispenseSummaryResponse(
        Map<DispenseStatus, Long> byStatus,
        long dispensedToday
) {}
