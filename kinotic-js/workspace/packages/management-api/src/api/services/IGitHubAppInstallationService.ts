import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import { GitHubAppInstallation } from '@/api/model/github/GitHubAppInstallation'
import { GitHubInstallCompletion } from '@/api/model/github/GitHubInstallCompletion'

/**
 * Drives GitHub-linking for the caller's organization.
 *
 * The installation row is derived from GitHub rather than authored by a caller, so this
 * service exposes only the round-trip below plus a read and an unlink — it is deliberately
 * not a CRUD proxy.
 */
export interface IGitHubAppInstallationService {

    /**
     * Stages a single-use state token bound to the caller's organization plus the
     * supplied returnTo, then returns the GitHub install URL with that state
     * embedded. The SPA performs {@code window.location = url}.
     *
     * returnTo is echoed back from {@link completeInstall} and may carry query
     * params (e.g. {@code /projects?openNewProject=1}) so the destination page
     * can pick up where the user was.
     */
    startInstall(returnTo: string | null): Promise<string>

    /**
     * Finalises the install once GitHub has redirected the browser back to the SPA
     * callback. Consumes the staged state, exchanges the redirect's user-authorization
     * code, and persists the {@link GitHubAppInstallation} row only when GitHub reports
     * the authorizing user can access the claimed installation. Returns the row along
     * with the original returnTo.
     *
     * An organization holds one installation at a time: re-completing for the installation
     * already bound refreshes it, while binding a second one rejects until {@link unlink}
     * runs.
     */
    completeInstall(installationId: number, state: string, code: string): Promise<GitHubInstallCompletion>

    /**
     * Returns the installation bound to the caller's organization, or null if GitHub
     * is not yet linked. Drives the "linked / not linked" indicator in the
     * org-settings UI.
     */
    findForCurrentOrg(): Promise<GitHubAppInstallation | null>

    /**
     * Removes the caller's organization's GitHub link. Resolves once the removal is
     * visible to {@link findForCurrentOrg}. No-op when nothing is linked.
     */
    unlink(): Promise<void>

}

export class GitHubAppInstallationService implements IGitHubAppInstallationService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.os.api.services.GitHubAppInstallationService`)
    }

    public startInstall(returnTo: string | null): Promise<string> {
        return this.serviceProxy.invoke('startInstall', [returnTo])
    }

    public completeInstall(installationId: number, state: string, code: string): Promise<GitHubInstallCompletion> {
        return this.serviceProxy.invoke('completeInstall', [installationId, state, code])
    }

    public findForCurrentOrg(): Promise<GitHubAppInstallation | null> {
        return this.serviceProxy.invoke('findForCurrentOrg', [])
    }

    public unlink(): Promise<void> {
        return this.serviceProxy.invoke('unlink', [])
    }
}
