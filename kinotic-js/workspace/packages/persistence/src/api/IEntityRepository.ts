import type { QueryParameter } from '@/api/model/QueryParameter'
import { Kinotic } from '@kinotic-ai/core'
import { type Page, type Pageable, type IterablePage } from '@kinotic-ai/core'
import { EntitiesRepository, type IEntitiesRepository } from '@/api/IEntitiesRepository'

/**
 * This is the base interface for all entity repositories.
 * It provides the basic CRUD operations for entities.
 */
export interface IEntityRepository<T> {

    /**
     * The organizationId of the Entity this repository is for
     */
    entityOrganizationId: string

    /**
     * The applicationId of the Entity this repository is for
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
     * Saves all given entities.
     * @param entities all the entities to save
     * @return Promise that will complete when all entities have been saved
     */
    bulkSave(entities: T[]): Promise<void>;

    /**
     * Updates all given entities.
     * @param entities all the entities to save
     * @return Promise that will complete when all entities have been saved
     */
    bulkUpdate(entities: T[]): Promise<void>;

    /**
     * Returns the number of entities available.
     *
     * @return Promise emitting the number of entities
     */
    count(): Promise<number>;

    /**
     * Returns the number of entities available for the given query.
     *
     * @param query       the query used to limit result
     * @return Promise    emitting the number of entities
     */
    countByQuery(query: string): Promise<number>;

    /**
     * Deletes the entity with the given id.
     *
     * @param id      must not be {@literal null}
     * @return Promise signaling when operation has completed
     * @throws Error in case the given {@literal id} is {@literal null}
     */
    deleteById(id: string): Promise<void>;

    /**
     * Deletes the entity with the given id.
     *
     * @param query      the query used to filter records to delete, must not be {@literal null}
     * @return Promise signaling when operation has completed
     * @throws Error in case the given {@literal query} is {@literal null}
     */
    deleteByQuery(query: string): Promise<void>;

    /**
     * Returns a {@link Page} of entities meeting the paging restriction provided in the {@link Pageable} object.
     *
     * @param pageable the page settings to be used
     * @return a page of entities
     */
    findAll(pageable: Pageable): Promise<IterablePage<T>>;

    /**
     * Retrieves an entity by its id.
     *
     * @param id      must not be {@literal null}
     * @return Promise emitting the entity with the given id or Promise emitting null if none found
     * @throws Error in case the given {@literal id} is {@literal null}
     */
    findById(id: string): Promise<T | null>;

    /**
     * Retrieves a list of entities by their id.
     *
     * @param ids      must not be {@literal null}
     * @return Promise emitting the entities with the given ids or Promise emitting null if none found
     * @throws Error in case the given {@literal ids} is {@literal null}
     */
    findByIds(ids: string[]): Promise<T[]>;

    /**
     * Executes a named query.
     * @param queryName the name of the function that defines the query
     * @param queryParameters to pass to the query
     * @returns Promise with the result of the query
     */
    namedQuery<U>(queryName: string, queryParameters: QueryParameter[]): Promise<U>

    /**
     * Executes a named query and returns a Page of results.
     * @param queryName the name of the function that defines the query
     * @param queryParameters to pass to the query
     * @param pageable the page settings to be used
     * @returns Promise with the result of the query
     */
    namedQueryPage<U>(queryName: string, queryParameters: QueryParameter[], pageable: Pageable): Promise<IterablePage<U>>

    /**
     * Saves a given entity. This will override all data if there is an existing entity with the same id.
     * Use the returned instance for further operations as the save operation might have changed the entity instance.
     *
     * @param entity  must not be {@literal null}
     * @return Promise emitting the saved entity
     * @throws Error in case the given {@literal entity} is {@literal null}
     */
    save(entity: T): Promise<T>;

    /**
     * Returns a {@link Page} of entities matching the search text and paging restriction provided in the {@link Pageable} object.
     *
     * @param searchText the text to search for entities for
     * @param pageable   the page settings to be used
     * @return a page of entities
     */
    search(searchText: string, pageable: Pageable): Promise<IterablePage<T>>;

    /**
     * This operation makes all the recent writes immediately available for search.
     * @return a Promise that resolves when the operation is complete
     */
    syncIndex(): Promise<void>

    /**
     * Updates a given entity. This will only override the fields that are present in the given entity.
     * If any fields are not present in the given entity data they will not be changed.
     * If the entity does not exist it will be created.
     *
     * @param entity      must not be {@literal null}
     * @return Promise emitting the saved entity
     * @throws Error in case the given {@literal entity} is {@literal null}
     */
    update(entity: T): Promise<T>;

}

/**
 * This is the base class for all entity repositories.
 * It provides the basic CRUD operations for entities.
 */
export class EntityRepository<T> implements IEntityRepository<T> {

    public entityOrganizationId: string
    public entityApplicationId: string
    public entityName: string
    public entityId: string

    private readonly entitiesRepository: IEntitiesRepository

    public constructor(entityOrganizationId: string,
                       entityApplicationId: string,
                       entityName: string,
                       entitiesRepository?: IEntitiesRepository) {
        this.entityOrganizationId = entityOrganizationId
        this.entityApplicationId = entityApplicationId
        this.entityName = entityName
        this.entityId = (entityOrganizationId + '.' + entityApplicationId + '.' + entityName).toLowerCase()
        this.entitiesRepository = entitiesRepository ?? new EntitiesRepository(Kinotic)
    }

    public async bulkSave(entities: T[]): Promise<void> {
        const entitiesToSave = await this.beforeBulkSaveOrUpdate(entities)
        return this.entitiesRepository.bulkSave(this.entityId, entitiesToSave)
    }

    public async bulkUpdate(entities: T[]): Promise<void> {
        const entitiesToSave = await this.beforeBulkSaveOrUpdate(entities)
        return this.entitiesRepository.bulkUpdate(this.entityId, entitiesToSave)
    }

    public count(): Promise<number> {
        return this.entitiesRepository.count(this.entityId)
    }

    public countByQuery(query: string): Promise<number> {
        return this.entitiesRepository.countByQuery(this.entityId, query)
    }

    public deleteById(id: string): Promise<void> {
        return this.entitiesRepository.deleteById(this.entityId, id)
    }

    public deleteByQuery(query: string): Promise<void> {
        return this.entitiesRepository.deleteByQuery(this.entityId, query)
    }

    public findAll(pageable: Pageable): Promise<IterablePage<T>> {
        return this.entitiesRepository.findAll(this.entityId, pageable)
    }

    public findById(id: string): Promise<T | null> {
        return this.entitiesRepository.findById(this.entityId, id)
    }

    public findByIds(ids: string[]): Promise<T[]> {
        return this.entitiesRepository.findByIds(this.entityId, ids)
    }

    public namedQuery<U>(queryName: string, queryParameters: QueryParameter[]): Promise<U> {
        return this.entitiesRepository.namedQuery(this.entityId, queryName, queryParameters)
    }

    public namedQueryPage<U>(queryName: string, queryParameters: QueryParameter[], pageable: Pageable): Promise<IterablePage<U>> {
        return this.entitiesRepository.namedQueryPage(this.entityId, queryName, queryParameters, pageable)
    }

    public async save(entity: T): Promise<T> {
        const entityToSave = await this.beforeSaveOrUpdate(entity)
        return this.entitiesRepository.save(this.entityId, entityToSave)
    }

    public search(searchText: string, pageable: Pageable): Promise<IterablePage<T>> {
        return this.entitiesRepository.search(this.entityId, searchText, pageable)
    }

    public async syncIndex(): Promise<void> {
        return this.entitiesRepository.syncIndex(this.entityId)
    }

    public async update(entity: T): Promise<T> {
        const entityToSave = await this.beforeSaveOrUpdate(entity)
        return this.entitiesRepository.update(this.entityId, entityToSave)
    }

    protected async beforeBulkSaveOrUpdate(entities: T[]): Promise<T[]> {
        return Promise.resolve(entities)
    }

    protected async beforeSaveOrUpdate(entity: T): Promise<T> {
        return Promise.resolve(entity)
    }
}