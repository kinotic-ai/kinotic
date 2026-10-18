import type { Identifiable } from '@kinotic-ai/core'

/**
 * A person as a member of one scope, for listing a scope's members: an account joined with its membership
 * of an organization or of the platform, or an application's own user. {@link enabled} is the person's
 * standing in this scope alone.
 */
export class Member implements Identifiable<string> {
    /** The id of the person's identity, which member operations name. */
    public id: string | null = null

    public email: string = ''

    public displayName: string | null = null

    /** Whether the person may sign in to this scope. */
    public enabled: boolean = true

    /** When the person became a member of this scope. */
    public joined: string | null = null
}
