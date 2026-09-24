import type { StatusConditionType } from './StatusConditionType'

/**
 * Something a watcher has inferred about a record beside what the record's authority reports. A
 * record holds at most one condition of each type.
 */
export interface StatusCondition {
    /** What was inferred. */
    type: StatusConditionType
    /** Why, for an operator. */
    message: string
    /** When the inference was first made, epoch milliseconds. */
    since: number
}
