import type { Resource } from '@/api/model/security/Resource'
import type { Subject } from '@/api/model/security/Subject'

/**
 * A role granted to a subject on a resource. The subject holds every permission the role bundles on the
 * resource and on everything inside it.
 */
export interface Grant {
    /** The grant's id, which revokes it. */
    id: string
    /** The role granted, a built-in role's id or a custom role's. */
    roleId: string
    /** Who holds it. */
    subject: Subject
    /** Where it was made. */
    resource: Resource
}
