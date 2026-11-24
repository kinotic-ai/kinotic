import type { SubjectType } from '@/api/model/security/SubjectType'

/**
 * Who a grant is made to: a user by its identity id, or a group, a tenant or an application by its id, which stands
 * for every member the group, every user the tenant, or every end user the application, has at the time of a check.
 */
export interface Subject {
    /** Whether the id names a user, a group, a tenant or an application. */
    type: SubjectType
    /** The user's identity id, the group's id, the tenant's id or the application's id. */
    id: string
}
