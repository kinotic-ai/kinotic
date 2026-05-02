import type { Identifiable } from '@kinotic-ai/core'

/**
 * Persisted link between a Kinotic Project and a single GitHub repository reachable
 * through an existing {@link GitHubAppInstallation}. Drives webhook dispatch
 * (delivery → project) and ref-creation auth (project → which repo, via which
 * installation).
 */
export class ProjectGitHubRepoLink implements Identifiable<string> {
    public id: string | null = null
    public projectId: string = ''
    public organizationId: string = ''
    public installationId: string = ''
    public repoFullName: string = ''
    public repoId: number = 0
    public defaultBranch: string = ''
    public updated: number | null = null
}
