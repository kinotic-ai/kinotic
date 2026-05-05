import { CrudServiceProxy, type IKinotic, type ICrudServiceProxy } from '@kinotic-ai/core'
import { GitHubAppInstallation } from '@/api/model/github/GitHubAppInstallation'
import { GitHubInstallCompletion } from '@/api/model/github/GitHubInstallCompletion'

export interface IGitHubAppInstallationService extends ICrudServiceProxy<GitHubAppInstallation> {

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
     * callback. Consumes the staged state, fetches the install details from GitHub,
     * persists the {@link GitHubAppInstallation} row, and returns it along with the
     * original intent and returnTo.
     */
    completeInstall(installationId: number, state: string): Promise<GitHubInstallCompletion>

    /**
     * Returns the (at-most-one) installation bound to the caller's organization, or
     * null if GitHub is not yet linked. Drives the "linked / not linked" indicator
     * in the org-settings UI.
     */
    findForCurrentOrg(): Promise<GitHubAppInstallation | null>

}

export class GitHubAppInstallationService extends CrudServiceProxy<GitHubAppInstallation>
    implements IGitHubAppInstallationService {

    constructor(kinotic: IKinotic) {
        super(kinotic.serviceProxy('org.kinotic.github.api.services.GitHubAppInstallationService'))
    }

    public startInstall(returnTo: string | null): Promise<string> {
        return this.serviceProxy.invoke('startInstall', [returnTo])
    }

    public completeInstall(installationId: number, state: string): Promise<GitHubInstallCompletion> {
        return this.serviceProxy.invoke('completeInstall', [installationId, state])
    }

    public findForCurrentOrg(): Promise<GitHubAppInstallation | null> {
        return this.serviceProxy.invoke('findForCurrentOrg', [])
    }
}
