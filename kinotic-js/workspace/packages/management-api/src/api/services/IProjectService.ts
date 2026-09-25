import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import { CrudServiceProxy, FunctionalIterablePage, type IKinotic, type ICrudServiceProxy, type IterablePage, type Page, type Pageable } from '@kinotic-ai/core'
import { Project } from '@/api/model/Project'
import type { ProjectDeployment } from '@/api/model/ProjectDeployment'
import type { WatchEvent } from '@/api/model/WatchEvent'

export interface IProjectService extends ICrudServiceProxy<Project> {

    /**
     * Counts all projects for the given application.
     * @param applicationId the application to find projects for
     * @return Promise emitting the number of projects
     */
    countForApplication(applicationId: string): Promise<number>

    /**
     * Creates a new project if it does not already exist.
     * @param project the project to create
     * @return Promise emitting the created project or the existing project if it already exists
     */
    createProjectIfNotExist(project: Project): Promise<Project>

    /**
     * Finds all projects for the given application.
     * @param applicationId the application to find projects for
     * @param pageable the page to return
     * @return Promise emitting a page of projects
     */
    findAllForApplication(applicationId: string, pageable: Pageable): Promise<IterablePage<Project>>

    /**
     * Finds the deployment record of the given project in the current participant's
     * organization.
     * @param projectId id of the project the deployment belongs to
     * @return Promise emitting the deployment record, or null when the project has never
     *         been deployed
     */
    findDeployment(projectId: string): Promise<ProjectDeployment | null>

    /**
     * Lists what happened to the deployment of the given project in the current participant's
     * organization and to the records it made, the deployment jobs, build VMs, microservice
     * deployments and UI deployments, newest first, with what caused each. A project that has never
     * been deployed has none.
     * @param projectId id of the project the deployment belongs to
     * @param pageable the page to return
     */
    findDeploymentHistory(projectId: string, pageable: Pageable): Promise<IterablePage<WatchEvent>>

    /**
     * Re-runs repository initialization for a project left
     * {@link RepositoryConnectionStatus.INITIALIZATION_FAILED} by creation.
     * @param projectId the id of the project to retry
     * @return Promise emitting the project, marked {@link RepositoryConnectionStatus.CONNECTED} on success
     */
    retryRepoInitialization(projectId: string): Promise<Project>

    /**
     * This operation makes all the recent writes immediately available for search.
     * @return a Promise that resolves when the operation is complete
     */
    syncIndex(): Promise<void>

}

export class ProjectService extends CrudServiceProxy<Project> implements IProjectService {

    constructor(kinotic: IKinotic) {
        super(kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.ProjectService`))
    }

    public countForApplication(applicationId: string): Promise<number> {
        return this.serviceProxy.invoke('countForApplication', [applicationId])
    }

    public createProjectIfNotExist(project: Project): Promise<Project> {
        return this.serviceProxy.invoke('createProjectIfNotExist', [project])
    }

    public async findAllForApplication(applicationId: string, pageable: Pageable): Promise<IterablePage<Project>> {
        const page: Page<Project> = await this.findAllForApplicationSinglePage(applicationId, pageable)
        return new FunctionalIterablePage(pageable, page,
            (pageable: Pageable) => this.findAllForApplicationSinglePage(applicationId, pageable))
    }

    public findAllForApplicationSinglePage(applicationId: string, pageable: Pageable): Promise<IterablePage<Project>> {
        return this.serviceProxy.invoke('findAllForApplication', [applicationId, pageable])
    }

    public findDeployment(projectId: string): Promise<ProjectDeployment | null> {
        return this.serviceProxy.invoke('findDeployment', [projectId])
    }

    public async findDeploymentHistory(projectId: string, pageable: Pageable): Promise<IterablePage<WatchEvent>> {
        const page: Page<WatchEvent> = await this.findDeploymentHistorySinglePage(projectId, pageable)
        return new FunctionalIterablePage(pageable, page,
            (pageable: Pageable) => this.findDeploymentHistorySinglePage(projectId, pageable))
    }

    public findDeploymentHistorySinglePage(projectId: string, pageable: Pageable): Promise<Page<WatchEvent>> {
        return this.serviceProxy.invoke('findDeploymentHistory', [projectId, pageable])
    }

    public retryRepoInitialization(projectId: string): Promise<Project> {
        return this.serviceProxy.invoke('retryRepoInitialization', [projectId])
    }

    public syncIndex(): Promise<void> {
        return this.serviceProxy.invoke('syncIndex', [])
    }

}
