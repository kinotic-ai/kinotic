import type { QueryParameter } from '@/api/model/QueryParameter'
import type { TenantSpecificId } from '@/api/model/TenantSpecificId'
import type { IKinotic } from '@kinotic-ai/core'
import {
    type IServiceProxy,
    type Page,
    type Pageable,
    type IterablePage,
    FunctionalIterablePage
} from '@kinotic-ai/core'

export type TenantSelection = string[]

export interface IAdminEntitiesRepository {

    /**
     * Returns the number of entities available.
     * @param structureId the id of the structure to count
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @return {@link Promise} emitting the number of entities.
     */
    count(entityDefinitionId: string, tenantSelection: TenantSelection): Promise<number>

    /**
     * Returns the number of entities available for the given query.
     *
     * @param entityDefinitionId the id of the structure to save the entity for
     * @param query       the query used to limit result
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @return Promise    emitting the number of entities
     */
    countByQuery(entityDefinitionId: string, query: string, tenantSelection: TenantSelection): Promise<number>

    /**
     * Deletes the entity with the given id.
     *
     * @param entityDefinitionId the id of the structure to save the entity for
     * @param id          must not be {@literal null}
     * @return {@link Promise} emitting when delete is complete
     * @throws IllegalArgumentException in case the given {@literal id} is {@literal null}
     */
    deleteById(entityDefinitionId: string, id: TenantSpecificId): Promise<void>

    /**
     * Deletes the entity with the given id.
     *
     * @param entityDefinitionId the id of the structure to save the entity for
     * @param query      the query used to filter records to delete, must not be {@literal null}
     * @param tenantSelection the list of tenants to use when deleting the entity records
     * @return Promise signaling when operation has completed
     * @throws Error in case the given {@literal query} is {@literal null}
     */
    deleteByQuery(entityDefinitionId: string, query: string, tenantSelection: TenantSelection): Promise<void>

    /**
     * Returns a {@link IterablePage} of entities meeting the paging restriction provided in the {@link Pageable} object.
     *
     * @param entityDefinitionId the id of the structure to save the entity for
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @param pageable    the page settings to be used
     * @return a page of entities
     */
    findAll<T>(entityDefinitionId: string, tenantSelection: TenantSelection, pageable: Pageable): Promise<IterablePage<T>>

    /**
     * Retrieves an entity by its id.
     *
     * @param entityDefinitionId the id of the structure to save the entity for
     * @param id          must not be {@literal null}
     * @return {@link Promise} with the entity with the given id or {@link Promise} emitting null if none found
     * @throws IllegalArgumentException in case the given {@literal id} is {@literal null}
     */
    findById<T>(entityDefinitionId: string, id: TenantSpecificId): Promise<T | null>

    /**
     * Retrieves a list of entities by their id.
     *
     * @param entityDefinitionId the id of the structure to save the entity for
     * @param ids      must not be {@literal null}
     * @return Promise emitting the entities with the given ids or Promise emitting null if none found
     * @throws Error in case the given {@literal ids} is {@literal null}
     */
    findByIds<T>(entityDefinitionId: string, ids: TenantSpecificId[]): Promise<T[]>

    /**
     * Executes a named query.
     * @param entityDefinitionId the id of the structure that this named query is defined for
     * @param queryName the name of the function that defines the query
     * @param parameters to pass to the query
     * @returns Promise with the result of the query
     */
    namedQuery<T>(entityDefinitionId: string,
                  queryName: string,
                  parameters: QueryParameter[]): Promise<T>

    /**
     * Executes a named query and returns a Page of results.
     * @param entityDefinitionId the id of the structure that this named query is defined for
     * @param queryName the name of the function that defines the query
     * @param parameters to pass to the query
     * @param pageable the page settings to be used
     * @returns Promise with the result of the query
     */
    namedQueryPage<T>(entityDefinitionId: string,
                      queryName: string,
                      parameters: QueryParameter[],
                      pageable: Pageable): Promise<IterablePage<T>>

    /**
     * Returns a {@link IterablePage} of entities matching the search text and paging restriction provided in the {@link Pageable} object.
     *
     * @param entityDefinitionId the id of the structure to save the entity for
     * @param searchText  the text to search for entities for
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @param pageable    the page settings to be used
     * @return a page of entities
     */
    search<T>(entityDefinitionId: string, searchText: string, tenantSelection: TenantSelection, pageable: Pageable): Promise<IterablePage<T>>

}

export class AdminEntitiesRepository implements IAdminEntitiesRepository {

    protected serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy('org.kinotic.persistence.api.services.AdminJsonEntitiesRepository')
    }

    public count(entityDefinitionId: string, tenantSelection: TenantSelection): Promise<number> {
        return this.serviceProxy.invoke('count', [entityDefinitionId, tenantSelection])
    }

    public countByQuery(entityDefinitionId: string, query: string, tenantSelection: TenantSelection): Promise<number> {
        return this.serviceProxy.invoke('countByQuery', [entityDefinitionId, query, tenantSelection])
    }

    public deleteById(entityDefinitionId: string, id: TenantSpecificId): Promise<void> {
        return this.serviceProxy.invoke('deleteById', [entityDefinitionId, id])
    }

    public deleteByQuery(entityDefinitionId: string, query: string, tenantSelection: TenantSelection): Promise<void> {
        return this.serviceProxy.invoke('deleteByQuery', [entityDefinitionId, query, tenantSelection])
    }

    public async findAll<T>(entityDefinitionId: string, tenantSelection: TenantSelection, pageable: Pageable): Promise<IterablePage<T>> {
        const page: Page<T> = await this.findAllSinglePage(entityDefinitionId, tenantSelection, pageable)
        return new FunctionalIterablePage(pageable, page,
                                          (pageable: Pageable) => this.findAllSinglePage(entityDefinitionId, tenantSelection, pageable))
    }

    public async findAllSinglePage<T>(entityDefinitionId: string, tenantSelection: TenantSelection, pageable: Pageable): Promise<Page<T>> {
        return this.serviceProxy.invoke('findAll', [entityDefinitionId, tenantSelection, pageable])
    }

    public findById<T>(entityDefinitionId: string, id: TenantSpecificId): Promise<T> {
        return this.serviceProxy.invoke('findById', [entityDefinitionId, id])
    }

    public findByIds<T>(entityDefinitionId: string, ids: TenantSpecificId[]): Promise<T[]> {
        return this.serviceProxy.invoke('findByIds', [entityDefinitionId, ids])
    }

    public namedQuery<T>(entityDefinitionId: string,
                         queryName: string,
                         parameters: QueryParameter[]): Promise<T> {
        return this.serviceProxy.invoke('namedQuery', [entityDefinitionId, queryName, parameters])
    }

    public async namedQueryPage<T>(entityDefinitionId: string,
                                   queryName: string,
                                   parameters: QueryParameter[],
                                   pageable: Pageable): Promise<IterablePage<T>> {
        const page: Page<T> = await this.namedQuerySinglePage(entityDefinitionId, queryName, parameters, pageable)
        return new FunctionalIterablePage(pageable, page,
                                          (pageable: Pageable) => this.namedQuerySinglePage(entityDefinitionId,
                                                                                            queryName,
                                                                                            parameters,
                                                                                            pageable))
    }

    public namedQuerySinglePage<T>(entityDefinitionId: string,
                                   queryName: string,
                                   parameters: QueryParameter[],
                                   pageable: Pageable): Promise<Page<T>> {
        return this.serviceProxy.invoke('namedQueryPage', [entityDefinitionId, queryName, parameters, pageable])
    }

    public async search<T>(entityDefinitionId: string,
                           searchText: string,
                           tenantSelection: TenantSelection,
                           pageable: Pageable): Promise<IterablePage<T>> {
        const page: Page<T> = await this.searchSinglePage(entityDefinitionId, searchText, tenantSelection, pageable)
        return new FunctionalIterablePage(pageable, page,
                                          (pageable: Pageable) => this.searchSinglePage(entityDefinitionId, searchText, tenantSelection, pageable))
    }

    public async searchSinglePage<T>(entityDefinitionId: string,
                                     searchText: string,
                                     tenantSelection: TenantSelection,
                                     pageable: Pageable): Promise<Page<T>> {
        return this.serviceProxy.invoke('search', [entityDefinitionId, searchText, tenantSelection, pageable])
    }
}