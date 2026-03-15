import { CrudServiceProxy, FunctionalIterablePage, type IKinotic, type ICrudServiceProxy, type IterablePage, type Page, type Pageable } from '@kinotic-ai/core'
import type { EntityDefinition } from '@/api/domain/EntityDefinition'


export interface IEntityDefinitionService extends ICrudServiceProxy<EntityDefinition> {

    /**
     * Counts all entity definitions for the given application.
     * @param applicationId the application to find entity definitions for
     * @return Promise emitting the number of entity definitions
     */
    countForApplication(applicationId: string): Promise<number>

    /**
     * Counts all entity definitions for the given project.
     * @param projectId the project to find entity definitions for
     * @return Promise emitting the number of entity definitions
     */
    countForProject(projectId: string): Promise<number>

    /**
     * Finds all entity definitions for the given application.
     * @param applicationId the application to find entity definitions for
     * @param pageable the page to return
     * @return Promise emitting a page of entity definitions
     */
    findAllForApplication(applicationId: string, pageable: Pageable): Promise<IterablePage<EntityDefinition>>

    /**
     * Finds all published entity definitions for the given application.
     * @param applicationId the application to find entity definitions for
     * @param pageable the page to return
     * @return Promise emitting a page of entity definitions
     */
    findAllPublishedForApplication(applicationId: string, pageable: Pageable): Promise<Page<EntityDefinition>>

    /**
     * Finds all entity definitions for the given project.
     * @param projectId the project to find entity definitions for
     * @param pageable the page to return
     * @return Promise emitting a page of entity definitions
     */
    findAllForProject(projectId: string, pageable: Pageable): Promise<IterablePage<EntityDefinition>>

    /**
     * Publishes the entity definition with the given id.
     * This will make the entity definition available for use to read and write items for.
     * @param entityDefinitionId the id of the entity definition to publish
     * @return Promise that resolves when the entity definition has been published
     */
    publish(entityDefinitionId: string): Promise<void>

    /**
     * This operation makes all the recent writes immediately available for search.
     * @return a Promise that resolves when the operation is complete
     */
    syncIndex(): Promise<void>

    /**
     * Un-publish the entity definition with the given id.
     * @param entityDefinitionId the id of the entity definition to un-publish
     * @return Promise that resolves when the entity definition has been unpublished
     */
    unPublish(entityDefinitionId: string): Promise<void>
}

export class EntityDefinitionService extends CrudServiceProxy<EntityDefinition> implements IEntityDefinitionService {

    constructor(kinotic: IKinotic) {
        super(kinotic.serviceProxy('org.kinotic.persistence.api.services.EntityDefinitionService'))
    }

    public countForApplication(applicationId: string): Promise<number> {
        return this.serviceProxy.invoke('countForApplication', [applicationId])
    }

    public countForProject(projectId: string): Promise<number> {
        return this.serviceProxy.invoke('countForProject', [projectId])
    }

    public findAllForApplicationSinglePage(applicationId: string, pageable: Pageable): Promise<Page<EntityDefinition>> {
        return this.serviceProxy.invoke('findAllForApplication', [applicationId, pageable])
    }

    public async findAllForApplication(applicationId: string, pageable: Pageable): Promise<IterablePage<EntityDefinition>> {
        const page: Page<EntityDefinition> = await this.findAllForApplicationSinglePage(applicationId, pageable)
        return new FunctionalIterablePage(pageable, page,
            (pageable: Pageable) => this.findAllForApplicationSinglePage(applicationId, pageable))
    }

    public findAllPublishedForApplication(applicationId: string, pageable: Pageable): Promise<Page<EntityDefinition>> {
        return this.serviceProxy.invoke('findAllPublishedForApplication', [applicationId, pageable])
    }

    public async findAllForProject(projectId: string, pageable: Pageable): Promise<IterablePage<EntityDefinition>> {
        const page: Page<EntityDefinition> = await this.findAllForProjectSinglePage(projectId, pageable)
        return new FunctionalIterablePage(pageable, page,
            (pageable: Pageable) => this.findAllForProjectSinglePage(projectId, pageable))
    }

    public findAllForProjectSinglePage(projectId: string, pageable: Pageable): Promise<Page<EntityDefinition>> {
        return this.serviceProxy.invoke('findAllForProject', [projectId, pageable])
    }

    public publish(entityDefinitionId: string): Promise<void> {
        return this.serviceProxy.invoke('publish', [entityDefinitionId])
    }

    public unPublish(entityDefinitionId: string): Promise<void> {
        return this.serviceProxy.invoke('unPublish', [entityDefinitionId])
    }

    public syncIndex(): Promise<void> {
        return this.serviceProxy.invoke('syncIndex', [])
    }
}
