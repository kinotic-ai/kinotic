/**
 * Where an organization stands with paying for Kinotic Cloud.
 */
export enum BillingStatus {
    /** In its free trial, which needs no subscription. */
    TRIALING = 'TRIALING',
    /** Subscribed to a plan and paid up. */
    ACTIVE = 'ACTIVE',
    /**
     * Payment is due since pastDueSince: the trial ended with no subscription, a payment failed, or the subscription
     * was cancelled after the trial.
     */
    PAST_DUE = 'PAST_DUE'
}
