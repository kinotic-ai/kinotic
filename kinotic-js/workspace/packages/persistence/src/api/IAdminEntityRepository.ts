import type { QueryParameter } from '@/api/model/QueryParameter'
import type { TenantSpecificId } from '@/api/model/TenantSpecificId'
import { Kinotic } from '@kinotic-ai/core'
import { type Page, type Pageable, type IterablePage } from '@kinotic-ai/core'
import { AdminEntitiesRepository, type IAdminEntitiesRepository, type TenantSelection } from '@/api/IAdminEntitiesRepository'

/**
 * This is the base interface for all admin entity repositories.
 * It provides the basic CRUD operations for entities with multi-tenancy support.
 */
export interface IAdminEntityRepository<T> {

    /**
     * The organization id of the Entity this repository is for
     */
    entityOrganizationId: string

    /**
     * The application id of the Entity this repository is for
     */
    entityApplicationId: string

    /**
     * The name of the Entity this repository is for
     */
    entityName: string

    /**
     * The id of the Entity this repository is for
     * Which is the organizationId + '.' + applicationId + '.' + name
     */
    entityId: string

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
    findById(id: TenantSpecificId): Promise<T | null>;

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
 * This is the base class for all admin entity repositories.
 * It provides the basic CRUD operations for entities with multi-tenancy support.
 */
export class AdminEntityRepository<T> implements IAdminEntityRepository<T> {

    public entityOrganizationId: string
    public entityApplicationId: string
    public entityName: string
    public entityId: string

    private readonly adminEntitiesRepository: IAdminEntitiesRepository

    public constructor(entityOrganizationId: string,
                       entityApplicationId: string,
                       entityName: string,
                       adminEntitiesRepository?: IAdminEntitiesRepository) {
        this.entityOrganizationId = entityOrganizationId
        this.entityApplicationId = entityApplicationId
        this.entityName = entityName
        this.entityId = (entityOrganizationId + '.' + entityApplicationId + '.' + entityName).toLowerCase()
        this.adminEntitiesRepository = adminEntitiesRepository ?? new AdminEntitiesRepository(Kinotic)
    }

    public count(tenantSelection: TenantSelection): Promise<number> {
        return this.adminEntitiesRepository.count(this.entityId, tenantSelection)
    }

    public countByQuery(query: string, tenantSelection: TenantSelection): Promise<number> {
        return this.adminEntitiesRepository.countByQuery(this.entityId, query, tenantSelection)
    }

    public deleteById(id: TenantSpecificId): Promise<void> {
        return this.adminEntitiesRepository.deleteById(this.entityId, id)
    }

    public deleteByQuery(query: string, tenantSelection: TenantSelection): Promise<void> {
        return this.adminEntitiesRepository.deleteByQuery(this.entityId, query, tenantSelection)
    }

    public findAll(tenantSelection: TenantSelection, pageable: Pageable): Promise<IterablePage<T>> {
        return this.adminEntitiesRepository.findAll(this.entityId, tenantSelection, pageable)
    }

    public findById(id: TenantSpecificId): Promise<T> {
        return this.adminEntitiesRepository.findById(this.entityId, id)
    }

    public findByIds(ids: TenantSpecificId[]): Promise<T[]> {
        return this.adminEntitiesRepository.findByIds(this.entityId, ids)
    }

    public namedQuery<U>(queryName: string,
                         queryParameters: QueryParameter[]): Promise<U> {
        return this.adminEntitiesRepository.namedQuery(this.entityId, queryName, queryParameters)
    }

    public namedQueryPage<U>(queryName: string,
                             queryParameters: QueryParameter[],
                             pageable: Pageable): Promise<IterablePage<U>> {
        return this.adminEntitiesRepository.namedQueryPage(this.entityId, queryName, queryParameters, pageable)
    }

    public search(searchText: string, tenantSelection: TenantSelection, pageable: Pageable): Promise<IterablePage<T>> {
        return this.adminEntitiesRepository.search(this.entityId, searchText, tenantSelection, pageable)
    }

}