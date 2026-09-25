import type { StatusCondition } from './StatusCondition'
import type { StatusConditionType } from './StatusConditionType'

/**
 * A record's condition of the given type, or undefined when it carries none.
 */
export function findStatusCondition(conditions: StatusCondition[], type: StatusConditionType): StatusCondition | undefined {
    return conditions.find(condition => condition.type === type)
}

/**
 * Whether a record carries a condition of the given type.
 */
export function hasStatusCondition(conditions: StatusCondition[], type: StatusConditionType): boolean {
    return findStatusCondition(conditions, type) !== undefined
}
