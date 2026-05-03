import { GitHubAppInstallation } from '@/api/model/github/GitHubAppInstallation'

/**
 * Result of finalising a GitHub install round-trip. Returned to the SPA's callback
 * component so it can drive the post-install UX. {@code intent} and {@code returnTo}
 * echo what the SPA staged when it called {@code startInstall(...)}; both are null
 * for installs started without an intent.
 */
export class GitHubInstallCompletion {
    public installation!: GitHubAppInstallation
    public intent: string | null = null
    public returnTo: string | null = null
}
