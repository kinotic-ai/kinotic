import type { SubjectKind } from '@/api/model/security/SubjectKind'

/**
 * Who a grant is made to: a user by its identity id, or a group by its id, which stands for every member the
 * group has at the time of a check.
 */
export interface Subject {
    /** Whether the id names a user or a group. */
    kind: SubjectKind
    /** The user's identity id or the group's id. */
    id: string
}
