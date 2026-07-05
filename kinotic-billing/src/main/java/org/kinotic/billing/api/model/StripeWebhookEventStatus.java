package org.kinotic.billing.api.model;

/**
 * Processing state of a received Stripe webhook event.
 */
public enum StripeWebhookEventStatus {
    /** Signature-verified and persisted; processing not yet complete. */
    RECEIVED,
    PROCESSED,
    /** Last processing attempt failed; retries may still be pending. */
    FAILED,
    /** Retries exhausted; requires human attention. */
    DEAD_LETTERED
}
