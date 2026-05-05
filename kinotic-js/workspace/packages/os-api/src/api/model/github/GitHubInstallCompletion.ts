import { GitHubAppInstallation } from '@/api/model/github/GitHubAppInstallation'

/**
 * Result of finalising a GitHub install round-trip. {@code returnTo} echoes what
 * the SPA staged when it called {@code startInstall(...)} and may carry query
 * params (e.g. {@code /projects?openNewProject=1}) so the destination page can
 * pick up where the user was.
 */
export class GitHubInstallCompletion {
    public installation!: GitHubAppInstallation
    public returnTo: string | null = null
}
