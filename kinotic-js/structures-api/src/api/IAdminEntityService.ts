import {QueryParameter} from '@/api/domain/QueryParameter.js'
import {TenantSpecificId} from '@/api/domain/TenantSpecificId.js'
import {AdminEntitiesServiceSingleton, IAdminEntitiesService, TenantSelection} from '@/api/IAdminEntitiesService.js'
import {Page, Pageable, IterablePage} from '@kinotic/continuum-client'

/**
 * This is the base interface for all entity services.
 * It provides the basic CRUD operations for entities.
 */
export interface IAdminEntityService<T> {

    /**
     * The application id of the structure this service is for
     */
    structureApplicationId: string

    /**
     * The name of the structure this service is for
     */
    structureName: string

    /**
     * The id of the structure this service is for
     * Which is the applicationId + '.' + name
     */
    structureId: string

    /**
     * Returns the number of entities available.
     *
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @return Promise emitting the number of entities
     */
    count(tenantSelection: TenantSelection): Promise<number>;

    /**
     * Returns the number of entities available for the given query.
     *
     * @param query       the query used to limit result
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @return Promise    emitting the number of entities
     */
    countByQuery(query: string, tenantSelection: TenantSelection): Promise<number>;

    /**
     * Deletes the entity with the given id.
     *
     * @param id      must not be {@literal null}
     * @return Promise signaling when operation has completed
     * @throws Error in case the given {@literal id} is {@literal null}
     */
    deleteById(id: TenantSpecificId): Promise<void>;

    /**
     * Deletes the entity with the given id.
     *
     * @param query      the query used to filter records to delete, must not be {@literal null}
     * @param tenantSelection the list of tenants to use when deleting the entity records
     * @return Promise signaling when operation has completed
     * @throws Error in case the given {@literal query} is {@literal null}
     */
    deleteByQuery(query: string, tenantSelection: TenantSelection): Promise<void>;

    /**
     * Returns a {@link Page} of entities meeting the paging restriction provided in the {@link Pageable} object.
     *
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @param pageable the page settings to be used
     * @return a page of entities
     */
    findAll(tenantSelection: TenantSelection, pageable: Pageable): Promise<IterablePage<T>>;

    /**
     * Retrieves an entity by its id.
     *
     * @param id      must not be {@literal null}
     * @return Promise emitting the entity with the given id or Promise emitting null if none found
     * @throws Error in case the given {@literal id} is {@literal null}
     */
    findById(id: TenantSpecificId): Promise<T>;

    /**
     * Retrieves a list of entities by their id.
     *
     * @param ids      must not be {@literal null}
     * @return Promise emitting the entities with the given ids or Promise emitting null if none found
     * @throws Error in case the given {@literal ids} is {@literal null}
     */
    findByIds(ids: TenantSpecificId[]): Promise<T[]>;

    /**
     * Executes a named query.
     * @param queryName the name of the function that defines the query
     * @param queryParameters to pass to the query
     * @returns Promise with the result of the query
     */
    namedQuery<U>(queryName: string,
                  queryParameters: QueryParameter[]): Promise<U>

    /**
     * Executes a named query and returns a Page of results.
     * @param queryName the name of the function that defines the query
     * @param queryParameters to pass to the query
     * @param pageable the page settings to be used
     * @returns Promise with the result of the query
     */
    namedQueryPage<U>(queryName: string,
                      queryParameters: QueryParameter[],
                      pageable: Pageable): Promise<IterablePage<U>>

    /**
     * Returns a {@link Page} of entities matching the search text and paging restriction provided in the {@link Pageable} object.
     *
     * @param searchText the text to search for entities for
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @param pageable   the page settings to be used
     * @return a page of entities
     */
    search(searchText: string, tenantSelection: TenantSelection, pageable: Pageable): Promise<IterablePage<T>>;

}

/**
 * This is the base class for all entity services.
 * It provides the basic CRUD operations for entities.
 */
export class AdminEntityService<T> implements IAdminEntityService<T>{

    public structureApplicationId: string
    public structureName: string
    public structureId: string

    private readonly adminEntitiesService: IAdminEntitiesService

    public constructor(structureApplicationId: string,
                       structureName: string,
                       adminEntitiesService?: IAdminEntitiesService) {
        this.structureApplicationId = structureApplicationId
        this.structureName = structureName
        this.structureId = (structureApplicationId + '.' + structureName).toLowerCase()
        this.adminEntitiesService = adminEntitiesService || AdminEntitiesServiceSingleton
    }

    public count(tenantSelection: TenantSelection): Promise<number>{
        return this.adminEntitiesService.count(this.structureId, tenantSelection)
    }

    public countByQuery(query: string, tenantSelection: TenantSelection): Promise<number>{
        return this.adminEntitiesService.countByQuery(this.structureId, query, tenantSelection)
    }

    public deleteById(id: TenantSpecificId): Promise<void>{
        return this.adminEntitiesService.deleteById(this.structureId, id)
    }

    public deleteByQuery(query: string, tenantSelection: TenantSelection): Promise<void>{
        return this.adminEntitiesService.deleteByQuery(this.structureId, query, tenantSelection)
    }

    public findAll(tenantSelection: TenantSelection, pageable: Pageable): Promise<IterablePage<T>>{
        return this.adminEntitiesService.findAll(this.structureId, tenantSelection, pageable)
    }

    public findById(id: TenantSpecificId): Promise<T>{
        return this.adminEntitiesService.findById(this.structureId, id)
    }

    public findByIds(ids: TenantSpecificId[]): Promise<T[]>{
        return this.adminEntitiesService.findByIds(this.structureId, ids)
    }

    public namedQuery<U>(queryName: string,
                         queryParameters: QueryParameter[]): Promise<U> {
        return this.adminEntitiesService.namedQuery(this.structureId, queryName, queryParameters)
    }

    public namedQueryPage<U>(queryName: string,
                             queryParameters: QueryParameter[],
                             pageable: Pageable): Promise<IterablePage<U>> {
        return this.adminEntitiesService.namedQueryPage(this.structureId, queryName, queryParameters, pageable)
    }

    public search(searchText: string, tenantSelection: TenantSelection, pageable: Pageable): Promise<IterablePage<T>>{
        return this.adminEntitiesService.search(this.structureId, searchText, tenantSelection, pageable)
    }

}
