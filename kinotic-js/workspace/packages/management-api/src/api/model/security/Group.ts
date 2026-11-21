import type { Identifiable } from '@kinotic-ai/core'

/**
 * A group of an organization's members, so one grant reaches all of them. Its members are in the authorization
 * store; this record holds its name and description.
 */
export class Group implements Identifiable<string> {
    /** Minted by the server at creation. */
    public id: string | null = null

    /** The organization the group belongs to, set by the server from the caller. */
    public organizationId: string | null = null

    public name: string = ''

    public description: string | null = null

    public created: string | null = null

    public updated: string | null = null
}
