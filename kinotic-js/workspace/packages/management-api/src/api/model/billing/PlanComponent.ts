import type { PlanComponentType } from '@/api/model/billing/PlanComponentType'
import type { UsageMeter } from '@/api/model/billing/UsageMeter'

/**
 * One charge of a BillingPlan: a flat fee each billing interval, or a price for each unit of a meter used. Amounts are
 * in the smallest unit of the plan's currency, as Stripe takes them: cents for usd.
 */
export interface PlanComponent {
    type: PlanComponentType
    /** The fee each billing interval, for a FLAT component. */
    amount: number | null
    /** What is measured, for a METERED component. */
    meter: UsageMeter | null
    /** The price of each unit of meter beyond includedQuantity, for a METERED component; it may hold a fraction. */
    unitAmount: number | null
    /** How many units of meter each billing interval are free, for a METERED component. */
    includedQuantity: number
    stripePriceId: string | null
}
