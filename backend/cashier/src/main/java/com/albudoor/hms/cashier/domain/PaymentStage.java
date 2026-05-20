package com.albudoor.hms.cashier.domain;

/**
 * Where in the visit lifecycle this payment sits. Drives whether/how visits auto-transition
 * when a payment is approved or rejected.
 */
public enum PaymentStage {
    /** First payment of a visit — approval moves visit AWAITING_PAYMENT → IN_PROGRESS. */
    INITIAL,
    /** Payment created when one visit is forwarded into another — approval moves the
     *  forwarded sub-visit AWAITING_PAYMENT → IN_PROGRESS. */
    REFERRAL,
    /** Discharge payment for bed-stay departments. Approval moves visit
     *  AWAITING_FINAL_PAYMENT → COMPLETED; rejection → OUTSTANDING_BALANCE. */
    FINAL,
    /** Auto-billed when bed period of stay is extended — does not move the visit. */
    STAY_EXTENSION,
    /** Pharmacy dispense payment — does not change visit state. */
    PHARMACY
}
