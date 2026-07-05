package org.kinotic.billing.api.model;

/**
 * How often the payout scheduler sweeps an organization's payable balance.
 */
public enum PayoutInterval {
    DAILY,
    WEEKLY,
    MONTHLY,
    /** No automatic sweeps; payouts are initiated explicitly. */
    MANUAL
}
