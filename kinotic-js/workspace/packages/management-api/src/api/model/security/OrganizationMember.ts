import type { Identifiable } from '@kinotic-ai/core'

/**
 * A member of one organization, for listing the organization's members: a user of the organization with whether
 * anyone signs in as it yet.
 */
export class OrganizationMember implements Identifiable<string> {
    /** The id of the member's user, which member operations name. */
    public id: string = ''

    public email: string = ''

    public displayName: string | null = null

    /** Whether the member may sign in to the organization. */
    public enabled: boolean = true

    /** When the member was added. */
    public joined: string | null = null

    /**
     * Whether the member has yet to sign in for the first time: an administrator added them and no sign-in method
     * claims the member yet, so the invitation they were sent, or the organization's own provider, is what
     * admits them.
     */
    public pending: boolean = false
}
