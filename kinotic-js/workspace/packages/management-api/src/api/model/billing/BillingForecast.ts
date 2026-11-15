import type { UsageMeter } from '@/api/model/billing/UsageMeter'

/**
 * What an organization's current billing period is expected to cost, projecting its usage so far over the whole
 * period. Amounts are in the smallest unit of currency.
 */
export interface BillingForecast {
    planId: string
    currency: string
    periodStart: string
    periodEnd: string
    /** The usage of each meter since periodStart. */
    usageToDate: Partial<Record<UsageMeter, number>>
    /** The usage of each meter by periodEnd if it goes on at the rate it has since periodStart. */
    projectedUsage: Partial<Record<UsageMeter, number>>
    /** The plan's flat fees plus the projected usage beyond what the plan includes, before tax and discounts. */
    projectedAmount: number
}
