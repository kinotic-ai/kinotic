package org.kinotic.billing.api.model;

/**
 * Kinotic-side billing policy state for an organization, independent of the Stripe
 * account's own state ({@link RecipientAccountStatus}).
 */
public enum OrgBillingStatus {
    ACTIVE,
    /** Earnings accrue but payout sweeps are withheld (e.g. risk review). */
    PAYOUTS_HELD,
    SUSPENDED
}
