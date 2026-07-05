package org.kinotic.billing.api.model;

/**
 * Lifecycle of a {@link RevenueSplit} from charge settlement through refunds and disputes.
 */
public enum RevenueSplitStatus {
    /** Charge seen but not yet settled (no balance transaction). */
    PENDING,
    /** Split computed and the organization's ledger credited. */
    EARNED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    /** A dispute is open against the underlying charge. */
    DISPUTED,
    /** Dispute lost or refund clawback completed. */
    CLAWED_BACK
}
