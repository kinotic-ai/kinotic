import {Kinotic, type IServiceProxy} from '@kinotic-ai/core'

export interface GitHubAppInstallation {
    id: string
    organizationId: string
    githubInstallationId: string
    accountLogin: string
    accountType: string
    suspendedAt?: string | null
    created?: string
    updated?: string
}

/**
 * Service proxy for the published org-scoped GitHubAppInstallationService. Drives
 * the entire link/unlink flow over the existing STOMP session — no REST calls or
 * JWT replays from the SPA. {@code startInstall} returns the GitHub install URL
 * (with a server-staged state token); the SPA navigates to it. GitHub redirects
 * back to {@code /api/github/install/callback}, which is the only REST hop and
 * needs no SPA-side auth.
 */
export class GitHubAppInstallationService {

    private serviceProxy: IServiceProxy

    constructor() {
        this.serviceProxy = Kinotic.serviceProxy(
            'org.kinotic.os.github.api.services.GitHubAppInstallationService')
    }

    public startInstall(): Promise<string> {
        return this.serviceProxy.invoke('startInstall', [])
    }

    public findForCurrentOrg(): Promise<GitHubAppInstallation | null> {
        return this.serviceProxy.invoke('findForCurrentOrg', [])
    }

    public deleteById(id: string): Promise<void> {
        return this.serviceProxy.invoke('deleteById', [id])
    }
}

export const GITHUB_APP_INSTALLATION_SERVICE = new GitHubAppInstallationService()
