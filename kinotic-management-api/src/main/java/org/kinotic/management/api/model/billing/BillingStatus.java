package org.kinotic.management.api.model.billing;

/**
 * Where an organization stands with paying for Kinotic Cloud.
 */
public enum BillingStatus {
    /** In its free trial, which needs no subscription. */
    TRIALING,
    /** Subscribed to a plan and paid up. */
    ACTIVE,
    /**
     * Payment is due since {@link BillingAccount#getPastDueSince()}: the trial ended with no subscription, a payment
     * failed, or the subscription was cancelled after the trial.
     */
    PAST_DUE
}
