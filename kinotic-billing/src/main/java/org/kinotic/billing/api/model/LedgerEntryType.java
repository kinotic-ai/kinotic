package org.kinotic.billing.api.model;

/**
 * The kind of money movement a {@link LedgerEntry} represents on an organization's
 * payable balance. Positive-amount types credit the organization; negative-amount types
 * debit it.
 */
public enum LedgerEntryType {
    /** Net share from a successful end-user charge (+). */
    EARNING,
    /** Organization's share of a refunded charge (−). */
    REFUND_CLAWBACK,
    /** Disputed amount frozen while a dispute is open (−). */
    DISPUTE_HOLD,
    /** Dispute won: hold released (+). */
    DISPUTE_RELEASE,
    /** Card-network dispute fee allocation (−). */
    DISPUTE_FEE,
    /** Rolling reserve withheld per the organization's reserve policy (−). */
    RESERVE_HOLD,
    /** Reserve released after the hold period (+). */
    RESERVE_RELEASE,
    /** Minimum platform fee netted from earnings (−). */
    PLATFORM_FEE_FLOOR,
    /** Usage-based platform fee computed by the billing aggregator (−). */
    PLATFORM_FEE_USAGE,
    /** Funds transferred to the organization's recipient account (−). */
    TRANSFER,
    /** A transfer that failed or was reversed (+). */
    TRANSFER_REVERSAL,
    /** Manual correction; requires a memo (±). */
    ADJUSTMENT
}
