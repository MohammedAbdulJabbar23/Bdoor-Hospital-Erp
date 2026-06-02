package com.albudoor.hms.cashier.summary;

import com.albudoor.hms.cashier.domain.PaymentMethod;
import com.albudoor.hms.cashier.domain.PaymentStage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Close-of-day reconciliation for a single date: APPROVED payment totals for that day broken
 * down by payment method and by stage.
 *
 * <p>VIP-bypass is reported as its own method row but is also surfaced separately
 * ({@code vipBypass}) and excluded from {@code cashCollected}, because no money actually
 * changed hands for VIP-bypassed approvals.
 *
 * @param date          the day reconciled (cashier's local date)
 * @param byMethod      per-payment-method totals (includes a VIP_BYPASS row when present)
 * @param byStage       per-stage totals
 * @param grandTotal    sum + count across ALL approved payments that day (incl. VIP-bypass)
 * @param vipBypass     the VIP-bypass slice broken out (not real cash)
 * @param cashCollected grand total MINUS VIP-bypass — the real money collected that day
 */
public record ReconciliationResponse(
        LocalDate date,
        List<Bucket> byMethod,
        List<Bucket> byStage,
        Total grandTotal,
        Total vipBypass,
        Total cashCollected
) {
    /** One labelled total row: the {@code key} is a method or stage name (string for JSON friendliness). */
    public record Bucket(String key, BigDecimal total, long count) {}

    /** A bare sum + count, no label. */
    public record Total(BigDecimal total, long count) {

        static Total empty() {
            return new Total(BigDecimal.ZERO, 0L);
        }
    }

    static Bucket bucket(PaymentMethod method, BigDecimal total, long count) {
        return new Bucket(method == null ? "UNKNOWN" : method.name(), total, count);
    }

    static Bucket bucket(PaymentStage stage, BigDecimal total, long count) {
        return new Bucket(stage == null ? "UNKNOWN" : stage.name(), total, count);
    }
}
