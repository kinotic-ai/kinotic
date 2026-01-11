import {QueryParameter} from '@/api/domain/QueryParameter'
import {
    Continuum,
    IServiceProxy,
    Page,
    Pageable,
    IterablePage,
    IServiceRegistry,
    FunctionalIterablePage
} from '@mindignited/continuum-client'

export interface IEntitiesService {

    /**
     * Saves all given entities.
     * @param structureId the id of the structure to save the entities for
     * @param entities all the entities to save
     * @return Promise that will complete when all entities have been saved
     */
    bulkSave<T>(structureId: string, entities: T[]): Promise<void>

    /**
     * Updates all given entities.
     * @param structureId the id of the structure to update the entities for
     * @param entities all the entities to save
     * @return Promise that will complete when all entities have been saved
     */
    bulkUpdate<T>(structureId: string, entities: T[]): Promise<void>

    /**
     * Returns the number of entities available.
     * @param structureId the id of the structure to count
     * @return {@link Promise} emitting the number of entities.
     */
    count(structureId: string): Promise<number>

    /**
     * Returns the number of entities available for the given query.
     *
     * @param structureId the id of the structure to save the entity for
     * @param query       the query used to limit result
     * @return Promise    emitting the number of entities
     */
    countByQuery(structureId: string, query: string): Promise<number>

    /**
     * Deletes the entity with the given id.
     *
     * @param structureId the id of the structure to save the entity for
     * @param id          must not be {@literal null}
     * @return {@link Promise} emitting when delete is complete
     * @throws IllegalArgumentException in case the given {@literal id} is {@literal null}
     */
    deleteById(structureId: string, id: string): Promise<void>

    /**
     * Deletes the entity with the given id.
     *
     * @param structureId the id of the structure to save the entity for
     * @param query      the query used to filter records to delete, must not be {@literal null}
     * @return Promise signaling when operation has completed
     * @throws Error in case the given {@literal query} is {@literal null}
     */
    deleteByQuery(structureId: string, query: string): Promise<void>

    /**
     * Returns a {@link IterablePage} of entities meeting the paging restriction provided in the {@link Pageable} object.
     *
     * @param structureId the id of the structure to save the entity for
     * @param pageable    the page settings to be used
     * @return a page of entities
     */
    findAll<T>(structureId: string, pageable: Pageable): Promise<IterablePage<T>>

    /**
     * Returns a single {@link Page} of entities meeting the paging restriction provided in the {@link Pageable} object.
     * @param structureId the id of the structure to save the entity for
     * @param pageable the page settings to be used
     */
    findAllSinglePage<T>(structureId: string, pageable: Pageable): Promise<Page<T>>

    /**
     * Retrieves an entity by its id.
     *
     * @param structureId the id of the structure to save the entity for
     * @param id          must not be {@literal null}
     * @return {@link Promise} with the entity with the given id or {@link Promise} emitting null if none found
     * @throws IllegalArgumentException in case the given {@literal id} is {@literal null}
     */
    findById<T>(structureId: string, id: string): Promise<T>

    /**
     * Retrieves a list of entities by their id.
     *
     * @param structureId the id of the structure to save the entity for
     * @param ids      must not be {@literal null}
     * @return Promise emitting the entities with the given ids or Promise emitting null if none found
     * @throws Error in case the given {@literal ids} is {@literal null}
     */
    findByIds<T>(structureId: string, ids: string[]): Promise<T[]>

    /**
     * Executes a named query.
     * @param structureId the id of the structure that this named query is defined for
     * @param queryName the name of the function that defines the query
     * @param parameters to pass to the query
     * @returns Promise with the result of the query
     */
    namedQuery<T>(structureId: string, queryName: string, parameters: QueryParameter[]): Promise<T>

    /**
     * Executes a named query and returns a Page of results.
     * @param structureId the id of the structure that this named query is defined for
     * @param queryName the name of the function that defines the query
     * @param parameters to pass to the query
     * @param pageable the page settings to be used
     * @returns Promise with the result of the query
     */
    namedQueryPage<T>(structureId: string, queryName: string, parameters: QueryParameter[], pageable: Pageable): Promise<IterablePage<T>>

    /**
     * Saves a given entity. Use the returned instance for further operations as the save operation might have changed the
     * entity instance completely.
     *
     * @param structureId the id of the structure to save the entity for
     * @param entity      must not be {@literal null}
     * @return {@link Promise} emitting the saved entity
     * @throws IllegalArgumentException in case the given {@literal entity} is {@literal null}
     */
    save<T>(structureId: string, entity: T): Promise<T>

    /**
     * Returns a {@link IterablePage} of entities matching the search text and paging restriction provided in the {@link Pageable} object.
     * <p>
     * You can find more information about the search syntax <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#query-string-syntax">here</a>
     *
     * @param structureId the id of the structure to save the entity for
     * @param searchText  the text to search for entities for
     * @param pageable    the page settings to be used
     * @return a page of entities
     */
    search<T>(structureId: string, searchText: string, pageable: Pageable): Promise<IterablePage<T>>

    /**
     * Returns a single {@link Page} of entities matching the search text and paging restriction provided in the {@link Pageable} object.
     * <p>
     * You can find more information about the search syntax <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#query-string-syntax">here</a>
     *
     * @param structureId the id of the structure to save the entity for
     * @param searchText  the text to search for entities for
     * @param pageable    the page settings to be used
     * @return a page of entities
     */
    searchSinglePage<T>(structureId: string, searchText: string, pageable: Pageable): Promise<Page<T>>

    /**
     * This operation makes all the recent writes immediately available for search.
     * @param structureId the id of the structure to sync the index for. (this is the {@link Structure#getApplication()} + "." + {@link Structure#getName()})
     * @return a Promise that resolves when the operation is complete
     */
    syncIndex(structureId: string): Promise<void>

    /**
     * Updates a given entity. This will only override the fields that are present in the given entity.
     * If any fields are not present in the given entity data, they will not be changed.
     * If the entity does not exist, it will be created.
     * Use the returned instance for further operations as the save operation might have changed the entity instance.
     *
     * @param structureId the id of the structure to update the entity for
     * @param entity      must not be {@literal null}
     * @return Promise emitting the saved entity
     * @throws Error in case the given {@literal entity} is {@literal null}
     */
    update<T>(structureId: string, entity: T): Promise<T>

}

export class EntitiesService implements IEntitiesService {

    protected serviceProxy: IServiceProxy

     constructor(serviceRegistry?: IServiceRegistry) {
        const service = 'org.mindignited.structures.api.services.JsonEntitiesService'
        this.serviceProxy = serviceRegistry?.serviceProxy(service) || Continuum.serviceProxy(service)
    }

    public bulkSave<T>(structureId: string, entities: T[]): Promise<void>{
        return this.serviceProxy.invoke('bulkSave', [structureId, entities])
    }

    public bulkUpdate<T>(structureId: string, entities: T[]): Promise<void>{
        return this.serviceProxy.invoke('bulkUpdate', [structureId, entities])
    }

    public count(structureId: string): Promise<number> {
        return this.serviceProxy.invoke('count', [structureId])
    }

    public countByQuery(structureId: string, query: string): Promise<number> {
        return this.serviceProxy.invoke('countByQuery', [structureId, query])
    }

    public deleteById(structureId: string, id: string): Promise<void> {
        return this.serviceProxy.invoke('deleteById', [structureId, id])
    }

    public deleteByQuery(structureId: string, query: string): Promise<void> {
        return this.serviceProxy.invoke('deleteByQuery', [structureId, query])
    }

    public async findAll<T>(structureId: string, pageable: Pageable): Promise<IterablePage<T>> {
        const page: Page<T> = await this.findAllSinglePage(structureId, pageable)
        return new FunctionalIterablePage(pageable, page,
                                          (pageable: Pageable) => this.findAllSinglePage(structureId, pageable))
    }

    public async findAllSinglePage<T>(structureId: string, pageable: Pageable): Promise<Page<T>> {
        return this.serviceProxy.invoke('findAll', [structureId, pageable])
    }

    public findById<T>(structureId: string, id: string): Promise<T> {
        return this.serviceProxy.invoke('findById', [structureId, id])
    }

    public findByIds<T>(structureId: string, ids: string[]): Promise<T[]> {
        return this.serviceProxy.invoke('findByIds', [structureId, ids])
    }

    public namedQuery<T>(structureId: string, queryName: string, parameters: QueryParameter[]): Promise<T> {
        return this.serviceProxy.invoke('namedQuery', [structureId, queryName, parameters])
    }

    public async namedQueryPage<T>(structureId: string,
                                   queryName: string,
                                   parameters: QueryParameter[],
                                   pageable: Pageable): Promise<IterablePage<T>> {
        const page: Page<T> = await this.namedQuerySinglePage(structureId, queryName, parameters, pageable)
        return new FunctionalIterablePage(pageable, page,
                                          (pageable: Pageable) => this.namedQuerySinglePage(structureId, queryName, parameters, pageable))
    }

    public namedQuerySinglePage<T>(structureId: string,
                                   queryName: string,
                                   parameters: QueryParameter[],
                                   pageable: Pageable): Promise<Page<T  >> {
        return this.serviceProxy.invoke('namedQueryPage', [structureId, queryName, parameters, pageable])
    }

    public save<T>(structureId: string, entity: T): Promise<T> {
        return this.serviceProxy.invoke('save', [structureId, entity])
    }

    public async search<T>(structureId: string, searchText: string, pageable: Pageable): Promise<IterablePage<T>> {
        const page: Page<T> = await this.searchSinglePage(structureId, searchText, pageable)
        return new FunctionalIterablePage(pageable, page,
                                          (pageable: Pageable) => this.searchSinglePage(structureId, searchText, pageable))
    }

    public async searchSinglePage<T>(structureId: string, searchText: string, pageable: Pageable): Promise<Page<T>> {
        return this.serviceProxy.invoke('search', [structureId, searchText, pageable])
    }

    public async syncIndex(structureId: string): Promise<void> {
        return this.serviceProxy.invoke('syncIndex', [structureId])
    }

    public update<T>(structureId: string, entity: T): Promise<T>{
        return this.serviceProxy.invoke('update', [structureId, entity])
    }
}

export const EntitiesServiceSingleton: IEntitiesService = new EntitiesService()
