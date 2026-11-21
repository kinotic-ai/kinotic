import type { SubjectType } from '@/api/model/security/SubjectType'

/**
 * Who a grant is made to: a user by its identity id, or a group or a tenant by its id, which stands for every
 * member the group, or every user the tenant, has at the time of a check.
 */
export interface Subject {
    /** Whether the id names a user, a group or a tenant. */
    type: SubjectType
    /** The user's identity id, the group's id or the tenant's id. */
    id: string
}
