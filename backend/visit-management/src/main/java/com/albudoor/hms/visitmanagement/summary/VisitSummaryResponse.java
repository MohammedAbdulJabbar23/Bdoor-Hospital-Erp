package com.albudoor.hms.visitmanagement.summary;

import com.albudoor.hms.visitmanagement.domain.VisitStatus;

import java.util.Map;

/**
 * Server-computed visit-queue KPIs. All counts come from DB {@code count}/{@code group by}
 * queries, never by paging+summing client-side, so they stay correct regardless of queue size
 * (the row listing remains paginated and capped, but these totals are not).
 *
 * @param byStatus       per-{@link VisitStatus} total counts (only non-zero statuses need be
 *                       present; consumers default missing statuses to 0)
 * @param completedToday number of visits that COMPLETED today (by {@code endedAt}) — the
 *                       "Completed today" tile counts these, not every COMPLETED visit ever
 */
public record VisitSummaryResponse(
        Map<VisitStatus, Long> byStatus,
        long completedToday
) {}
