package com.albudoor.hms.cashier.summary;

import com.albudoor.hms.cashier.domain.PaymentStage;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Server-computed cashier KPIs — all aggregates are derived from DB {@code count}/{@code sum}
 * queries, never by paging+summing client-side, so they stay correct at any scale.
 *
 * @param pendingCount        number of PENDING payments
 * @param pendingTotal        sum of {@code totalDue} across PENDING payments
 * @param receivedToday       cash actually collected today: sum of {@code totalDue} of payments
 *                            APPROVED today, EXCLUDING VIP-bypass (no money changed hands)
 * @param approvedTodayCount  number of payments APPROVED today (excluding VIP-bypass)
 * @param pendingByStage      per-stage pending counts (only non-zero stages need be present;
 *                            consumers default missing stages to 0)
 */
public record PaymentSummaryResponse(
        long pendingCount,
        BigDecimal pendingTotal,
        BigDecimal receivedToday,
        long approvedTodayCount,
        Map<PaymentStage, Long> pendingByStage
) {}
