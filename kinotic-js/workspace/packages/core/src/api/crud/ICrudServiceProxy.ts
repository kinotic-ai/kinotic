import type {Identifiable, IterablePage} from '@/index';
import type {Page} from './Page';
import {Pageable} from './Pageable'
import type {IEditableDataSource} from "./IDataSource";

/**
 * A {@link ICrudServiceProxy} is a proxy for a remote CRUD service
 */
export interface ICrudServiceProxy<T extends Identifiable<string>> extends IEditableDataSource<T>{

    /**
     * Creates a new entity if one does not already exist for the given id
     * @param entity to create if one does not already exist
     * @return a {@link Promise} containing the new entity or an error if an exception occurred
     */
    create(entity: T): Promise<T>

    /**
     * Creates a new entity if one does not already exist for the given id, and waits for the
     * change to be visible in search results before returning.
     * Use this when you need read-your-write consistency immediately after creation.
     *
     * @param entity to create if one does not already exist
     * @return a {@link Promise} containing the new entity after it is searchable, or an error if an exception occurred
     */
    createSync(entity: T): Promise<T>

    /**
     * Saves a given entity. Use the returned instance for further operations as the save operation might have changed the
     * entity instance completely.
     *
     * @param entity must not be {@literal null}.
     * @return a {@link Promise} emitting the saved entity.
     * @throws Error in case the given {@literal entity} is {@literal null}.
     */
    save(entity: T): Promise<T>

    /**
     * Saves a given entity and waits for the changes to be visible in search results before returning.
     * Use the returned instance for further operations as the save operation might have changed the entity instance completely.
     *
     * @param entity must not be {@literal null}.
     * @return a {@link Promise} emitting the saved entity.
     * @throws Error in case the given {@literal entity} is {@literal null}.
     */
    saveSync(entity: T): Promise<T>

    /**
     * Retrieves an entity by its id.
     *
     * @param id must not be {@literal null}.
     * @return a {@link Promise} emitting the entity with the given id or {@link Promise#empty()} if none found.
     * @throws IllegalArgumentException in case the given {@literal identity} is {@literal null}.
     */
    findById(id: string): Promise<T>

    /**s
     * Returns the number of entities available.
     *
     * @return a {@link Promise} emitting the number of entities.
     */
    count(): Promise<number>

    /**
     * Deletes the entity with the given id.
     *
     * @param id must not be {@literal null}.
     * @return a {@link Promise} signaling when operation has completed.
     * @throws IllegalArgumentException in case the given {@literal identity} is {@literal null}.
     */
    deleteById(id: string): Promise<void>

    /**
     * Returns a {@link Page} of entities meeting the paging restriction provided in the {@code Pageable} object.
     *
     * @param pageable the page settings to be used
     * @return a {@link Promise} emitting the page of entities
     */
    findAll(pageable: Pageable): Promise<IterablePage<T>>

    /**
     * Returns a {@link Page} of entities not in the ids list and meeting the paging restriction provided in the {@code Pageable} object.
     *
     * @param ids not to be returned in the Page
     * @param pageable the page settings to be used
     * @return a {@link Promise} emitting the page of entities
     */
    findByIdNotIn(ids: string[], pageable: Pageable): Promise<Page<Identifiable<string>>>

    /**
     * Returns a {@link Page} of entities matching the search text and paging restriction provided in the {@code Pageable} object.
     *
     * @param searchText the text to search for entities for
     * @param pageable the page settings to be used
     * @return a {@link Promise} emitting the page of entities
     */
    search(searchText: string, pageable: Pageable): Promise<IterablePage<T>>

}
