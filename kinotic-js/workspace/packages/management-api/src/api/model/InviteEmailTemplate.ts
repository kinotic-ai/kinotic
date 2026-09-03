import type { Identifiable } from '@kinotic-ai/core'

/**
 * An application's customized invitation email — at most one per application. Subject and
 * bodies are Handlebars sources rendered with the same variables as the built-in
 * invitation template; when an application has no template, the built-in is used.
 */
export class InviteEmailTemplate implements Identifiable<string> {
    public id: string | null = null

    /** The organization that owns {@link applicationId}; set by the server on save. */
    public organizationId: string | null = null

    /** The application whose invitation email this template customizes. Always set. */
    public applicationId: string = ''

    /** Handlebars source for the subject line (rendered as plain text). */
    public subject: string = ''

    /** Handlebars source for the HTML body. */
    public htmlBody: string = ''

    /** Handlebars source for the plain-text body. */
    public textBody: string = ''

    public created: number | null = null

    public updated: number | null = null
}
