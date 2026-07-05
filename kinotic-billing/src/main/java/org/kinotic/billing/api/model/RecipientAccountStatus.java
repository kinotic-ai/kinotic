package org.kinotic.billing.api.model;

/**
 * Onboarding/verification state of an organization's Stripe recipient connected account.
 */
public enum RecipientAccountStatus {
    NOT_STARTED,
    PENDING_VERIFICATION,
    /** Transfers capability active — the account can receive funds. */
    ACTIVE,
    /** Stripe has restricted a capability (e.g. requirements past due). */
    RESTRICTED
}
