import {QueryParameter} from '@/api/domain/QueryParameter.js'
import {TenantSpecificId} from '@/api/domain/TenantSpecificId.js'
import {
    Continuum,
    IServiceProxy,
    Page,
    Pageable,
    IterablePage,
    IServiceRegistry,
    FunctionalIterablePage
} from '@kinotic/continuum-client'

export type TenantSelection = string[]

export interface IAdminEntitiesService {

    /**
     * Returns the number of entities available.
     * @param structureId the id of the structure to count
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @return {@link Promise} emitting the number of entities.
     */
    count(structureId: string, tenantSelection: TenantSelection): Promise<number>

    /**
     * Returns the number of entities available for the given query.
     *
     * @param structureId the id of the structure to save the entity for
     * @param query       the query used to limit result
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @return Promise    emitting the number of entities
     */
    countByQuery(structureId: string, query: string, tenantSelection: TenantSelection): Promise<number>

    /**
     * Deletes the entity with the given id.
     *
     * @param structureId the id of the structure to save the entity for
     * @param id          must not be {@literal null}
     * @return {@link Promise} emitting when delete is complete
     * @throws IllegalArgumentException in case the given {@literal id} is {@literal null}
     */
    deleteById(structureId: string, id: TenantSpecificId): Promise<void>

    /**
     * Deletes the entity with the given id.
     *
     * @param structureId the id of the structure to save the entity for
     * @param query      the query used to filter records to delete, must not be {@literal null}
     * @param tenantSelection the list of tenants to use when deleting the entity records
     * @return Promise signaling when operation has completed
     * @throws Error in case the given {@literal query} is {@literal null}
     */
    deleteByQuery(structureId: string, query: string, tenantSelection: TenantSelection): Promise<void>

    /**
     * Returns a {@link IterablePage} of entities meeting the paging restriction provided in the {@link Pageable} object.
     *
     * @param structureId the id of the structure to save the entity for
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @param pageable    the page settings to be used
     * @return a page of entities
     */
    findAll<T>(structureId: string, tenantSelection: TenantSelection, pageable: Pageable): Promise<IterablePage<T>>

    /**
     * Retrieves an entity by its id.
     *
     * @param structureId the id of the structure to save the entity for
     * @param id          must not be {@literal null}
     * @return {@link Promise} with the entity with the given id or {@link Promise} emitting null if none found
     * @throws IllegalArgumentException in case the given {@literal id} is {@literal null}
     */
    findById<T>(structureId: string, id: TenantSpecificId): Promise<T>

    /**
     * Retrieves a list of entities by their id.
     *
     * @param structureId the id of the structure to save the entity for
     * @param ids      must not be {@literal null}
     * @return Promise emitting the entities with the given ids or Promise emitting null if none found
     * @throws Error in case the given {@literal ids} is {@literal null}
     */
    findByIds<T>(structureId: string, ids: TenantSpecificId[]): Promise<T[]>

    /**
     * Executes a named query.
     * @param structureId the id of the structure that this named query is defined for
     * @param queryName the name of the function that defines the query
     * @param parameters to pass to the query
     * @returns Promise with the result of the query
     */
    namedQuery<T>(structureId: string,
                  queryName: string,
                  parameters: QueryParameter[]): Promise<T>

    /**
     * Executes a named query and returns a Page of results.
     * @param structureId the id of the structure that this named query is defined for
     * @param queryName the name of the function that defines the query
     * @param parameters to pass to the query
     * @param pageable the page settings to be used
     * @returns Promise with the result of the query
     */
    namedQueryPage<T>(structureId: string,
                      queryName: string,
                      parameters: QueryParameter[],
                      pageable: Pageable): Promise<IterablePage<T>>

    /**
     * Returns a {@link IterablePage} of entities matching the search text and paging restriction provided in the {@link Pageable} object.
     * <p>
     * You can find more information about the search syntax <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#query-string-syntax">here</a>
     *
     * @param structureId the id of the structure to save the entity for
     * @param searchText  the text to search for entities for
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @param pageable    the page settings to be used
     * @return a page of entities
     */
    search<T>(structureId: string, searchText: string, tenantSelection: TenantSelection, pageable: Pageable): Promise<IterablePage<T>>

}

export class AdminEntitiesService implements IAdminEntitiesService {

    protected serviceProxy: IServiceProxy

     constructor(serviceRegistry?: IServiceRegistry) {
        const service = 'org.mindignited.structures.api.services.AdminJsonEntitiesService'
        this.serviceProxy = serviceRegistry?.serviceProxy(service) || Continuum.serviceProxy(service)
    }

    public count(structureId: string, tenantSelection: TenantSelection): Promise<number> {
        return this.serviceProxy.invoke('count', [structureId, tenantSelection])
    }

    public countByQuery(structureId: string, query: string, tenantSelection: TenantSelection): Promise<number> {
        return this.serviceProxy.invoke('countByQuery', [structureId, query, tenantSelection])
    }

    public deleteById(structureId: string, id: TenantSpecificId): Promise<void> {
        return this.serviceProxy.invoke('deleteById', [structureId, id])
    }

    public deleteByQuery(structureId: string, query: string, tenantSelection: TenantSelection): Promise<void> {
        return this.serviceProxy.invoke('deleteByQuery', [structureId, query, tenantSelection])
    }

    public async findAll<T>(structureId: string, tenantSelection: TenantSelection, pageable: Pageable): Promise<IterablePage<T>> {
        const page: Page<T> = await this.findAllSinglePage(structureId, tenantSelection, pageable)
        return new FunctionalIterablePage(pageable, page,
                                          (pageable: Pageable) => this.findAllSinglePage(structureId, tenantSelection, pageable))
    }

    public async findAllSinglePage<T>(structureId: string, tenantSelection: TenantSelection, pageable: Pageable): Promise<Page<T>> {
        return this.serviceProxy.invoke('findAll', [structureId, tenantSelection, pageable])
    }

    public findById<T>(structureId: string, id: TenantSpecificId): Promise<T> {
        return this.serviceProxy.invoke('findById', [structureId, id])
    }

    public findByIds<T>(structureId: string, ids: TenantSpecificId[]): Promise<T[]> {
        return this.serviceProxy.invoke('findByIds', [structureId, ids])
    }

    public namedQuery<T>(structureId: string,
                         queryName: string,
                         parameters: QueryParameter[]): Promise<T> {
        return this.serviceProxy.invoke('namedQuery', [structureId, queryName, parameters])
    }

    public async namedQueryPage<T>(structureId: string,
                                   queryName: string,
                                   parameters: QueryParameter[],
                                   pageable: Pageable): Promise<IterablePage<T>> {
        const page: Page<T> = await this.namedQuerySinglePage(structureId, queryName, parameters, pageable)
        return new FunctionalIterablePage(pageable, page,
                                          (pageable: Pageable) => this.namedQuerySinglePage(structureId,
                                                                                            queryName,
                                                                                            parameters,
                                                                                            pageable))
    }

    public namedQuerySinglePage<T>(structureId: string,
                                   queryName: string,
                                   parameters: QueryParameter[],
                                   pageable: Pageable): Promise<Page<T  >> {
        return this.serviceProxy.invoke('namedQueryPage', [structureId, queryName, parameters, pageable])
    }

    public async search<T>(structureId: string,
                           searchText: string,
                           tenantSelection: TenantSelection,
                           pageable: Pageable): Promise<IterablePage<T>> {
        const page: Page<T> = await this.searchSinglePage(structureId, searchText, tenantSelection, pageable)
        return new FunctionalIterablePage(pageable, page,
                                          (pageable: Pageable) => this.searchSinglePage(structureId, searchText, tenantSelection, pageable))
    }

    public async searchSinglePage<T>(structureId: string,
                                     searchText: string,
                                     tenantSelection: TenantSelection,
                                     pageable: Pageable): Promise<Page<T>> {
        return this.serviceProxy.invoke('search', [structureId, searchText, tenantSelection, pageable])
    }
}

export const AdminEntitiesServiceSingleton: IAdminEntitiesService = new AdminEntitiesService()
