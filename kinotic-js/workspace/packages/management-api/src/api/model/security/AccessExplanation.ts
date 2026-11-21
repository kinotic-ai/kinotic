import type { Grant } from '@/api/model/security/Grant'

/**
 * Whether a subject holds a permission on a resource, and the grants on the resource and its ancestors it holds
 * the permission through: each one a role bundling the permission, made to the subject or to a group the subject
 * is in.
 */
export interface AccessExplanation {
    /** True when the subject holds the permission. */
    allowed: boolean
    /**
     * The grants it holds the permission through, in the order of the resource's lineage from the resource up;
     * empty when it holds the permission through nothing a grant explains, such as a membership.
     */
    through: Grant[]
}
