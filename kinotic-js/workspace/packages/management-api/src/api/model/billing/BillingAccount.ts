import type { Identifiable } from '@kinotic-ai/core'
import type { BillingStatus } from '@/api/model/billing/BillingStatus'

/**
 * An organization's standing with paying for Kinotic Cloud: its trial, the plan it subscribes to, and whether payment
 * is due. One account per organization, under the organization's id.
 */
export interface BillingAccount extends Identifiable<string> {
    id: string | null
    status: BillingStatus
    /** When the organization's free trial ends, or ended. */
    trialEndsAt: string
    /** The plan the organization subscribes to, or null when it has no subscription. */
    planId: string | null
    /** When the current billing interval of the subscription ends, or null without a subscription. */
    currentPeriodEnd: string | null
    /** Since when payment is due, while the account is PAST_DUE. */
    pastDueSince: string | null
    stripeCustomerId: string | null
    stripeSubscriptionId: string | null
    stripeCheckoutSessionId: string | null
    created: string | null
    updated: string | null
}
