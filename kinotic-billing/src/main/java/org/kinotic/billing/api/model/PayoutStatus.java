package org.kinotic.billing.api.model;

/**
 * Lifecycle of a {@link Payout} (one batched transfer to an organization's recipient account).
 */
public enum PayoutStatus {
    PENDING,
    IN_TRANSIT,
    COMPLETED,
    FAILED,
    REVERSED
}
