import type { Identifiable } from '@kinotic-ai/core'

/**
 * Console view of a pending invitation. Deliberately excludes the accept token — anyone who
 * can list invitations must not be able to redeem them.
 */
export class PendingInviteSummary implements Identifiable<string> {
    public id: string | null = null

    /** Email the invitation was sent to. */
    public email: string = ''

    /** Display name the inviter entered, if any. */
    public displayName: string | null = null

    /** The application the invitee would join; null for an organization-member invite. */
    public applicationId: string | null = null

    /** Display name of the member who sent the invitation. */
    public invitedByName: string | null = null

    public created: number | null = null

    /** When the invitation stops being acceptable. */
    public expiresAt: number | null = null
}
