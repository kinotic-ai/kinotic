import type { Identifiable } from '@kinotic-ai/core'
import type { UsageMeter } from '@/api/model/billing/UsageMeter'

/**
 * An organization's use of Kinotic Cloud during one hour, by UsageMeter, measured every five minutes from the workload
 * VMs the organization has running then.
 */
export interface UsageRecord extends Identifiable<string> {
    id: string | null
    organizationId: string
    /** The start of the hour. */
    hour: string
    /** The usage of each meter during the hour. */
    quantities: Partial<Record<UsageMeter, number>>
    /** The five-minute slots of the hour measured so far, numbered from 0 at the top of the hour. */
    measuredSlots: number[]
    /** When the hour was settled: reported to Stripe if billable, left unbilled otherwise. Null until then. */
    settled: string | null
    created: string | null
    updated: string | null
}
