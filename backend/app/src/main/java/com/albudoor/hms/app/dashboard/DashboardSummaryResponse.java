package com.albudoor.hms.app.dashboard;

/**
 * Read-only aggregate of live hospital metrics for the home dashboard.
 *
 * <p>KPIs (top tiles) + the "needs attention" counts. The UI formats
 * {@code bedsOccupied}/{@code bedsTotal} into "X / Y" and a percentage; the
 * attention counts drive which warning rows render (rows with count 0 hide).
 */
public record DashboardSummaryResponse(
        long patientsToday,
        long pendingPayments,
        long bedsOccupied,
        long bedsTotal,
        long activeQueues,
        long pendingPaymentsCount,
        long labResultsAwaiting,
        long bedsExpiringSoon
) {
}
