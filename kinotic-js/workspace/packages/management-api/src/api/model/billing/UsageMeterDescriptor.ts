import type { UsageCategory } from '@/api/model/billing/UsageCategory'
import type { UsageMeasurementType } from '@/api/model/billing/UsageMeasurementType'
import type { UsageMeter } from '@/api/model/billing/UsageMeter'

/**
 * What a UsageMeter is, for showing its usage: its name, unit, group and how it is measured.
 */
export interface UsageMeterDescriptor {
    meter: UsageMeter
    displayName: string
    /** The unit a quantity of the meter is in, such as GB-month. */
    unit: string
    category: UsageCategory
    measurementType: UsageMeasurementType
}
