import type { UsageMeter } from '@/api/model/billing/UsageMeter'

/**
 * The usage of one UsageMeter by one thing during the hour of a UsageRecord, such as the vCPU-hours of one microservice.
 */
export interface UsageLine {
    meter: UsageMeter
    /** The id of what used it, such as a microservice deployment's id or an application's id. */
    subjectId: string
    /** The name to show for what used it. */
    subjectName: string
    /** How much was used, in the meter's unit. */
    quantity: number
}
