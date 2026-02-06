import {Continuum, CrudServiceProxy, FunctionalIterablePage, ICrudServiceProxy, IterablePage, Page, Pageable} from '@kinotic/continuum-client'
import {Structure} from '@/api/domain/Structure'


export interface IStructureService extends ICrudServiceProxy<Structure> {

    /**
     * Counts all structures for the given application.
     * @param applicationId the application to find structures for
     * @return Promise emitting the number of structures
     */
    countForApplication(applicationId: string): Promise<number>

    /**
     * Counts all structures for the given project.
     * @param projectId the project to find structures for
     * @return Promise emitting the number of structures
     */
    countForProject(projectId: string): Promise<number>

    /**
     * Finds all structures for the given application.
     * @param applicationId the application to find structures for
     * @param pageable the page to return
     * @return Promise emitting a page of structures
     */
    findAllForApplication(applicationId: string, pageable: Pageable): Promise<IterablePage<Structure>>

    /**
     * Finds all published structures for the given application.
     * @param applicationId the application to find structures for
     * @param pageable the page to return
     * @return Promise emitting a page of structures
     */
    findAllPublishedForApplication(applicationId: string, pageable: Pageable): Promise<Page<Structure>>

    /**
     * Finds all structures for the given project.
     * @param projectId the project to find structures for
     * @param pageable the page to return
     * @return Promise emitting a page of structures
     */
    findAllForProject(projectId: string, pageable: Pageable): Promise<IterablePage<Structure>>

    /**
     * Publishes the structure with the given id.
     * This will make the structure available for use to read and write items for.
     * @param structureId the id of the structure to publish
     * @return Promise that resolves when the structure has been published
     */
    publish(structureId: string): Promise<void>

    /**
     * This operation makes all the recent writes immediately available for search.
     * @return a Promise that resolves when the operation is complete
     */
    syncIndex(): Promise<void>

    /**
     * Un-publish the structure with the given id.
     * @param structureId the id of the structure to un-publish
     * @return Promise that resolves when the structure has been unpublished
     */
    unPublish(structureId: string): Promise<void>
}

export class StructureService extends CrudServiceProxy<Structure> implements IStructureService{

    constructor() {
        super(Continuum.serviceProxy('org.kinotic.structures.api.services.StructureService'))
    }

    public countForApplication(applicationId: string): Promise<number> {
        return this.serviceProxy.invoke('countForApplication', [applicationId])
    }

    public countForProject(projectId: string): Promise<number> {
        return this.serviceProxy.invoke('countForProject', [projectId])
    }

    public findAllForApplicationSinglePage(applicationId: string, pageable: Pageable): Promise<Page<Structure>> {
        return this.serviceProxy.invoke('findAllForApplication', [applicationId, pageable])
    }

    public async findAllForApplication(applicationId: string, pageable: Pageable): Promise<IterablePage<Structure>> {
        const page: Page<Structure> = await this.findAllForApplicationSinglePage(applicationId, pageable)
        return new FunctionalIterablePage(pageable, page,
            (pageable: Pageable) => this.findAllForApplicationSinglePage(applicationId, pageable))
    }

    public findAllPublishedForApplication(applicationId: string, pageable: Pageable): Promise<Page<Structure>> {
        return this.serviceProxy.invoke('findAllPublishedForApplication', [applicationId, pageable])
    }

    public async findAllForProject(projectId: string, pageable: Pageable): Promise<IterablePage<Structure>> {
        const page: Page<Structure> = await this.findAllForProjectSinglePage(projectId, pageable)
        return new FunctionalIterablePage(pageable, page,
            (pageable: Pageable) => this.findAllForProjectSinglePage(projectId, pageable))
    }

    public findAllForProjectSinglePage(projectId: string, pageable: Pageable): Promise<Page<Structure>> {
        return this.serviceProxy.invoke('findAllForProject', [projectId, pageable])
    }

    public publish(structureId: string): Promise<void> {
        return this.serviceProxy.invoke('publish', [structureId])
    }

    public unPublish(structureId: string): Promise<void> {
        return this.serviceProxy.invoke('unPublish', [structureId])
    }

    public syncIndex(): Promise<void> {
        return this.serviceProxy.invoke('syncIndex', [])
    }
}
