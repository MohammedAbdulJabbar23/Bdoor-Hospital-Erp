package com.albudoor.hms.visitmanagement.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Visit lifecycle states + the valid transition graph between them. Centralised here
 * so domain operations on {@link Visit} can validate moves before mutating state.
 */
public enum VisitStatus {
    /** Visit just created, no payment record yet. */
    CREATED,
    /** Cashier payment record exists; awaiting approval. */
    AWAITING_PAYMENT,
    /** Payment approved; treatment / examination / study under way. */
    IN_PROGRESS,
    /** Forwarded to one or more sub-visits; awaiting their results. */
    AWAITING_RESULTS,
    /** Bed-stay departments only — clinical work complete, awaiting final cashier. */
    TREATMENT_FINISHED,
    /** Final discharge payment record created; awaiting cashier. */
    AWAITING_FINAL_PAYMENT,
    /** Visit closed normally. */
    COMPLETED,
    /** Visit cancelled before completion. */
    CANCELLED,
    /** Final payment rejected; visit stays open per locked decision. */
    OUTSTANDING_BALANCE;

    private static final Map<VisitStatus, Set<VisitStatus>> TRANSITIONS = Map.of(
            CREATED,                EnumSet.of(AWAITING_PAYMENT, CANCELLED),
            AWAITING_PAYMENT,       EnumSet.of(IN_PROGRESS, CANCELLED),
            IN_PROGRESS,            EnumSet.of(AWAITING_RESULTS, TREATMENT_FINISHED, COMPLETED, CANCELLED),
            AWAITING_RESULTS,       EnumSet.of(IN_PROGRESS, CANCELLED),
            TREATMENT_FINISHED,     EnumSet.of(AWAITING_FINAL_PAYMENT, CANCELLED),
            AWAITING_FINAL_PAYMENT, EnumSet.of(COMPLETED, OUTSTANDING_BALANCE),
            OUTSTANDING_BALANCE,    EnumSet.of(COMPLETED),
            COMPLETED,              EnumSet.noneOf(VisitStatus.class),
            CANCELLED,              EnumSet.noneOf(VisitStatus.class)
    );

    public boolean canTransitionTo(VisitStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
