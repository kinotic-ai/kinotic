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
 * Service proxy for the published org-scoped GitHubAppInstallationService. Drives the
 * "GitHub linked / not linked" indicator on the org-settings page and the unlink
 * action. The link flow itself goes through the gateway's REST endpoints — see
 * {@link startGitHubInstall}.
 */
export class GitHubAppInstallationService {

    private serviceProxy: IServiceProxy

    constructor() {
        this.serviceProxy = Kinotic.serviceProxy(
            'org.kinotic.os.github.api.services.GitHubAppInstallationService')
    }

    public findForCurrentOrg(): Promise<GitHubAppInstallation | null> {
        return this.serviceProxy.invoke('findForCurrentOrg', [])
    }

    public deleteById(id: string): Promise<void> {
        return this.serviceProxy.invoke('deleteById', [id])
    }
}

/**
 * Calls {@code POST /api/github/install/start} with the user's Kinotic JWT, then
 * navigates the browser to the GitHub-hosted install URL the server returned.
 */
export async function startGitHubInstall(jwt: string): Promise<void> {
    const resp = await fetch('/api/github/install/start', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Authorization': `Bearer ${jwt}` }
    })
    if (!resp.ok) {
        throw new Error(`install start failed: ${resp.status}`)
    }
    const body = await resp.json()
    window.location.href = body.url
}

export const GITHUB_APP_INSTALLATION_SERVICE = new GitHubAppInstallationService()
