import { CrudServiceProxy, type IKinotic, type ICrudServiceProxy } from '@kinotic-ai/core'
import { GitHubAppInstallation } from '@/api/model/github/GitHubAppInstallation'

export interface IGitHubAppInstallationService extends ICrudServiceProxy<GitHubAppInstallation> {

    /**
     * Stages a single-use {@code state} token bound to the caller's organization in
     * a cluster-wide store, then returns the GitHub install URL with that state
     * embedded. The SPA performs {@code window.location = url}.
     * <p>
     * Caller must be authenticated under {@code ORGANIZATION} scope; the org is read
     * from the participant. The state expires after 10 minutes if unused.
     */
    startInstall(): Promise<string>

    /**
     * Returns the (at-most-one) installation bound to the caller's organization, or
     * {@code null} if GitHub is not yet linked. Drives the "linked / not linked"
     * indicator in the org-settings UI.
     */
    findForCurrentOrg(): Promise<GitHubAppInstallation | null>

}

export class GitHubAppInstallationService extends CrudServiceProxy<GitHubAppInstallation>
    implements IGitHubAppInstallationService {

    constructor(kinotic: IKinotic) {
        super(kinotic.serviceProxy('org.kinotic.github.api.services.GitHubAppInstallationService'))
    }

    public startInstall(): Promise<string> {
        return this.serviceProxy.invoke('startInstall', [])
    }

    public findForCurrentOrg(): Promise<GitHubAppInstallation | null> {
        return this.serviceProxy.invoke('findForCurrentOrg', [])
    }
}
