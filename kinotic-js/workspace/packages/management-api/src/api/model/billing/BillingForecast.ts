import type { UsageMeter } from '@/api/model/billing/UsageMeter'

/**
 * What an organization's current billing period is expected to cost: its usage so far, plus what it runs and stores now
 * kept up to the period's end. Amounts are in the smallest unit of currency.
 */
export interface BillingForecast {
    planId: string
    currency: string
    periodStart: string
    periodEnd: string
    /** The usage of each meter since periodStart. */
    usageToDate: Partial<Record<UsageMeter, number>>
    /** The usage of each meter by periodEnd: the usage to date, plus what runs and is stored now for the rest of the period. */
    projectedUsage: Partial<Record<UsageMeter, number>>
    /** The plan's flat fees plus the projected usage beyond what the plan includes, before tax and discounts. */
    projectedAmount: number
}
