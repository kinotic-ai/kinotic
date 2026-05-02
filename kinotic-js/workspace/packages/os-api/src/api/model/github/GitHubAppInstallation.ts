import type { Identifiable } from '@kinotic-ai/core'

/**
 * Persisted record of one GitHub App installation that a Kinotic Org has authorised.
 * The durable binding that says "Org X has access to GitHub install Y" — without it,
 * no installation token can be minted on behalf of the org and webhook deliveries
 * can't be matched to a project.
 */
export class GitHubAppInstallation implements Identifiable<string> {
    public id: string | null = null
    public organizationId: string = ''
    public githubInstallationId: number = 0
    public accountLogin: string = ''
    public accountType: string = ''
    public suspendedAt: number | null = null
    public created: number | null = null
    public updated: number | null = null
}
