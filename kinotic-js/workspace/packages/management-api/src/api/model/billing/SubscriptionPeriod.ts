/**
 * The time one of an organization's Stripe subscriptions covers: usage during it is billed to that subscription.
 */
export interface SubscriptionPeriod {
    /** The id of the Stripe Subscription. */
    stripeSubscriptionId: string
    /** When the subscription started. */
    started: string
    /** When the subscription ended, or null while it runs. */
    ended: string | null
}
