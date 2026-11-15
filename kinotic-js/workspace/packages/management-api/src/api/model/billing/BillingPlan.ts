import type { Identifiable } from '@kinotic-ai/core'
import type { BillingInterval } from '@/api/model/billing/BillingInterval'
import type { PlanComponent } from '@/api/model/billing/PlanComponent'

/**
 * What an organization pays for Kinotic Cloud: a flat fee, metered usage, or both, each a PlanComponent, billed
 * together every interval.
 */
export interface BillingPlan extends Identifiable<string> {
    id: string | null
    /** The name organizations choose the plan by, and their invoices show. */
    name: string
    description: string | null
    /** The organization the plan is offered to alone, or null for a plan every organization may choose. */
    organizationId: string | null
    /** The ISO 4217 code of the currency the plan bills in, in lowercase, such as usd. */
    currency: string
    interval: BillingInterval
    components: PlanComponent[]
    archived: boolean
    stripeProductId: string | null
    created: string | null
    updated: string | null
}
