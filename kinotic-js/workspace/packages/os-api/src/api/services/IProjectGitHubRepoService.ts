import { CrudServiceProxy, type IKinotic, type ICrudServiceProxy } from '@kinotic-ai/core'
import { AvailableRepo } from '@/api/model/github/AvailableRepo'
import { ProjectGitHubRepoLink } from '@/api/model/github/ProjectGitHubRepoLink'

export interface IProjectGitHubRepoService extends ICrudServiceProxy<ProjectGitHubRepoLink> {

    /**
     * Lists the repositories visible under the caller's organization's installation.
     * Calls GitHub on every invocation; not cached because the org admin may have
     * just toggled repo access.
     */
    listAvailableRepos(): Promise<AvailableRepo[]>

    /**
     * Creates or replaces the link for the given project. Validates that the chosen
     * {@code repoFullName} is reachable through the org's installation before
     * persisting.
     */
    linkProject(projectId: string, repoFullName: string): Promise<ProjectGitHubRepoLink>

    /** Removes the link for the given project, if any. */
    unlinkProject(projectId: string): Promise<void>

    /** Returns the link for the given project, or {@code null} when none exists. */
    findByProject(projectId: string): Promise<ProjectGitHubRepoLink | null>

}

export class ProjectGitHubRepoService extends CrudServiceProxy<ProjectGitHubRepoLink>
    implements IProjectGitHubRepoService {

    constructor(kinotic: IKinotic) {
        super(kinotic.serviceProxy('org.kinotic.github.api.services.ProjectGitHubRepoService'))
    }

    public listAvailableRepos(): Promise<AvailableRepo[]> {
        return this.serviceProxy.invoke('listAvailableRepos', [])
    }

    public linkProject(projectId: string, repoFullName: string): Promise<ProjectGitHubRepoLink> {
        return this.serviceProxy.invoke('linkProject', [projectId, repoFullName])
    }

    public unlinkProject(projectId: string): Promise<void> {
        return this.serviceProxy.invoke('unlinkProject', [projectId])
    }

    public findByProject(projectId: string): Promise<ProjectGitHubRepoLink | null> {
        return this.serviceProxy.invoke('findByProject', [projectId])
    }
}
