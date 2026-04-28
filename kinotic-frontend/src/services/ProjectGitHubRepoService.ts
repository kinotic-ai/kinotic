import {Kinotic, type IServiceProxy} from '@kinotic-ai/core'

export interface AvailableRepo {
    repoId: string
    fullName: string
    defaultBranch: string
    privateRepo: boolean
}

export interface ProjectGitHubRepoLink {
    id: string
    projectId: string
    organizationId: string
    installationId: string
    repoFullName: string
    repoId: string
    defaultBranch: string
    updated?: string
}

/**
 * Service proxy for the published org-scoped ProjectGitHubRepoService. Drives the
 * project-settings repo dropdown and link/unlink actions.
 */
export class ProjectGitHubRepoService {

    private serviceProxy: IServiceProxy

    constructor() {
        this.serviceProxy = Kinotic.serviceProxy(
            'org.kinotic.os.github.api.services.ProjectGitHubRepoService')
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

export const PROJECT_GITHUB_REPO_SERVICE = new ProjectGitHubRepoService()
