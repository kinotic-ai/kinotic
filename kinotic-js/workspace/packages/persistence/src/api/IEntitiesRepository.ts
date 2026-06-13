import type { QueryParameter } from '@/api/model/QueryParameter'
import type { IKinotic } from '@kinotic-ai/core'
import {
    type IServiceProxy,
    type Page,
    type Pageable,
    type IterablePage,
    FunctionalIterablePage
} from '@kinotic-ai/core'

export interface IEntitiesRepository {

    /**
     * Saves all given entities.
     * @param entityDefinitionId the id of the structure to save the entities for
     * @param entities all the entities to save
     * @return Promise that will complete when all entities have been saved
     */
    bulkSave<T>(entityDefinitionId: string, entities: T[]): Promise<void>

    /**
     * Updates all given entities.
     * @param entityDefinitionId the id of the structure to update the entities for
     * @param entities all the entities to save
     * @return Promise that will complete when all entities have been saved
     */
    bulkUpdate<T>(entityDefinitionId: string, entities: T[]): Promise<void>

    /**
     * Returns the number of entities available.
     * @param entityDefinitionId the id of the structure to count
     * @return {@link Promise} emitting the number of entities.
     */
    count(entityDefinitionId: string): Promise<number>

    /**
     * Returns the number of entities available for the given query.
     *
     * @param entityDefinitionId the id of the structure to save the entity for
     * @param query       the query used to limit result
     * @return Promise    emitting the number of entities
     */
    countByQuery(entityDefinitionId: string, query: string): Promise<number>

    /**
     * Deletes the entity with the given id.
     *
     * @param entityDefinitionId the id of the structure to save the entity for
     * @param id          must not be {@literal null}
     * @return {@link Promise} emitting when delete is complete
     * @throws IllegalArgumentException in case the given {@literal id} is {@literal null}
     */
    deleteById(entityDefinitionId: string, id: string): Promise<void>

    /**
     * Deletes the entity with the given id.
     *
     * @param entityDefinitionId the id of the structure to save the entity for
     * @param query      the query used to filter records to delete, must not be {@literal null}
     * @return Promise signaling when operation has completed
     * @throws Error in case the given {@literal query} is {@literal null}
     */
    deleteByQuery(entityDefinitionId: string, query: string): Promise<void>

    /**
     * Returns a {@link IterablePage} of entities meeting the paging restriction provided in the {@link Pageable} object.
     *
     * @param entityDefinitionId the id of the structure to save the entity for
     * @param pageable    the page settings to be used
     * @return a page of entities
     */
    findAll<T>(entityDefinitionId: string, pageable: Pageable): Promise<IterablePage<T>>

    /**
     * Returns a single {@link Page} of entities meeting the paging restriction provided in the {@link Pageable} object.
     * @param entityDefinitionId the id of the structure to save the entity for
     * @param pageable the page settings to be used
     */
    findAllSinglePage<T>(entityDefinitionId: string, pageable: Pageable): Promise<Page<T>>

    /**
     * Retrieves an entity by its id.
     *
     * @param entityDefinitionId the id of the structure to save the entity for
     * @param id          must not be {@literal null}
     * @return {@link Promise} with the entity with the given id or {@link Promise} emitting null if none found
     * @throws IllegalArgumentException in case the given {@literal id} is {@literal null}
     */
    findById<T>(entityDefinitionId: string, id: string): Promise<T | null>

    /**
     * Retrieves a list of entities by their id.
     *
     * @param entityDefinitionId the id of the structure to save the entity for
     * @param ids      must not be {@literal null}
     * @return Promise emitting the entities with the given ids or Promise emitting null if none found
     * @throws Error in case the given {@literal ids} is {@literal null}
     */
    findByIds<T>(entityDefinitionId: string, ids: string[]): Promise<T[]>

    /**
     * Executes a named query.
     * @param entityDefinitionId the id of the structure that this named query is defined for
     * @param queryName the name of the function that defines the query
     * @param parameters to pass to the query
     * @returns Promise with the result of the query
     */
    namedQuery<T>(entityDefinitionId: string, queryName: string, parameters: QueryParameter[]): Promise<T>

    /**
     * Executes a named query and returns a Page of results.
     * @param entityDefinitionId the id of the structure that this named query is defined for
     * @param queryName the name of the function that defines the query
     * @param parameters to pass to the query
     * @param pageable the page settings to be used
     * @returns Promise with the result of the query
     */
    namedQueryPage<T>(entityDefinitionId: string, queryName: string, parameters: QueryParameter[], pageable: Pageable): Promise<IterablePage<T>>

    /**
     * Saves a given entity. Use the returned instance for further operations as the save operation might have changed the
     * entity instance completely.
     *
     * @param entityDefinitionId the id of the structure to save the entity for
     * @param entity      must not be {@literal null}
     * @return {@link Promise} emitting the saved entity
     * @throws IllegalArgumentException in case the given {@literal entity} is {@literal null}
     */
    save<T>(entityDefinitionId: string, entity: T): Promise<T>

    /**
     * Returns a {@link IterablePage} of entities matching the search text and paging restriction provided in the {@link Pageable} object.
     *
     * @param entityDefinitionId the id of the structure to save the entity for
     * @param searchText  the text to search for entities for
     * @param pageable    the page settings to be used
     * @return a page of entities
     */
    search<T>(entityDefinitionId: string, searchText: string, pageable: Pageable): Promise<IterablePage<T>>

    /**
     * Returns a single {@link Page} of entities matching the search text and paging restriction provided in the {@link Pageable} object.
     *
     * @param entityDefinitionId the id of the structure to save the entity for
     * @param searchText  the text to search for entities for
     * @param pageable    the page settings to be used
     * @return a page of entities
     */
    searchSinglePage<T>(entityDefinitionId: string, searchText: string, pageable: Pageable): Promise<Page<T>>

    /**
     * This operation makes all the recent writes immediately available for search.
     * @param entityDefinitionId the id of the structure to sync the index for.
     * @return a Promise that resolves when the operation is complete
     */
    syncIndex(entityDefinitionId: string): Promise<void>

    /**
     * Updates a given entity. This will only override the fields that are present in the given entity.
     * If any fields are not present in the given entity data, they will not be changed.
     * If the entity does not exist, it will be created.
     *
     * @param entityDefinitionId the id of the structure to update the entity for
     * @param entity      must not be {@literal null}
     * @return Promise emitting the saved entity
     * @throws Error in case the given {@literal entity} is {@literal null}
     */
    update<T>(entityDefinitionId: string, entity: T): Promise<T>

}

export class EntitiesRepository implements IEntitiesRepository {

    protected serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy('org.kinotic.persistence.api.services.JsonEntitiesRepository')
    }

    public bulkSave<T>(entityDefinitionId: string, entities: T[]): Promise<void> {
        return this.serviceProxy.invoke('bulkSave', [entityDefinitionId, entities])
    }

    public bulkUpdate<T>(entityDefinitionId: string, entities: T[]): Promise<void> {
        return this.serviceProxy.invoke('bulkUpdate', [entityDefinitionId, entities])
    }

    public count(entityDefinitionId: string): Promise<number> {
        return this.serviceProxy.invoke('count', [entityDefinitionId])
    }

    public countByQuery(entityDefinitionId: string, query: string): Promise<number> {
        return this.serviceProxy.invoke('countByQuery', [entityDefinitionId, query])
    }

    public deleteById(entityDefinitionId: string, id: string): Promise<void> {
        return this.serviceProxy.invoke('deleteById', [entityDefinitionId, id])
    }

    public deleteByQuery(entityDefinitionId: string, query: string): Promise<void> {
        return this.serviceProxy.invoke('deleteByQuery', [entityDefinitionId, query])
    }

    public async findAll<T>(entityDefinitionId: string, pageable: Pageable): Promise<IterablePage<T>> {
        const page: Page<T> = await this.findAllSinglePage(entityDefinitionId, pageable)
        return new FunctionalIterablePage(pageable, page,
                                          (pageable: Pageable) => this.findAllSinglePage(entityDefinitionId, pageable))
    }

    public async findAllSinglePage<T>(entityDefinitionId: string, pageable: Pageable): Promise<Page<T>> {
        return this.serviceProxy.invoke('findAll', [entityDefinitionId, pageable])
    }

    public findById<T>(entityDefinitionId: string, id: string): Promise<T | null> {
        return this.serviceProxy.invoke('findById', [entityDefinitionId, id])
    }

    public findByIds<T>(entityDefinitionId: string, ids: string[]): Promise<T[]> {
        return this.serviceProxy.invoke('findByIds', [entityDefinitionId, ids])
    }

    public namedQuery<T>(entityDefinitionId: string, queryName: string, parameters: QueryParameter[]): Promise<T> {
        return this.serviceProxy.invoke('namedQuery', [entityDefinitionId, queryName, parameters])
    }

    public async namedQueryPage<T>(entityDefinitionId: string,
                                   queryName: string,
                                   parameters: QueryParameter[],
                                   pageable: Pageable): Promise<IterablePage<T>> {
        const page: Page<T> = await this.namedQuerySinglePage(entityDefinitionId, queryName, parameters, pageable)
        return new FunctionalIterablePage(pageable, page,
                                          (pageable: Pageable) => this.namedQuerySinglePage(entityDefinitionId, queryName, parameters, pageable))
    }

    public namedQuerySinglePage<T>(entityDefinitionId: string,
                                   queryName: string,
                                   parameters: QueryParameter[],
                                   pageable: Pageable): Promise<Page<T>> {
        return this.serviceProxy.invoke('namedQueryPage', [entityDefinitionId, queryName, parameters, pageable])
    }

    public save<T>(entityDefinitionId: string, entity: T): Promise<T> {
        return this.serviceProxy.invoke('save', [entityDefinitionId, entity])
    }

    public async search<T>(entityDefinitionId: string, searchText: string, pageable: Pageable): Promise<IterablePage<T>> {
        const page: Page<T> = await this.searchSinglePage(entityDefinitionId, searchText, pageable)
        return new FunctionalIterablePage(pageable, page,
                                          (pageable: Pageable) => this.searchSinglePage(entityDefinitionId, searchText, pageable))
    }

    public async searchSinglePage<T>(entityDefinitionId: string, searchText: string, pageable: Pageable): Promise<Page<T>> {
        return this.serviceProxy.invoke('search', [entityDefinitionId, searchText, pageable])
    }

    public async syncIndex(entityDefinitionId: string): Promise<void> {
        return this.serviceProxy.invoke('syncIndex', [entityDefinitionId])
    }

    public update<T>(entityDefinitionId: string, entity: T): Promise<T> {
        return this.serviceProxy.invoke('update', [entityDefinitionId, entity])
    }
}