import type { Identifiable } from '@kinotic-ai/core'
import type { UsageLine } from '@/api/model/billing/UsageLine'

/**
 * An organization's usage of Kinotic Cloud during one hour, as a UsageLine for each meter and thing that used it.
 */
export interface UsageRecord extends Identifiable<string> {
    id: string | null
    organizationId: string
    /** The start of the hour. */
    hour: string
    lines: UsageLine[]
    /** When the hour was settled: reported to Stripe if billable, left unbilled otherwise. Null until then. */
    settled: string | null
    created: string | null
    updated: string | null
}
