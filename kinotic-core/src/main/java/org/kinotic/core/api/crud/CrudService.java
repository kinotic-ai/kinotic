package org.kinotic.core.api.crud;

import io.vertx.core.Future;
import org.kinotic.idl.api.annotations.McpToolInfo;

/**
 * Generic CRUD service interface for domain objects.
 * Created by Navíd Mitchell 🤪 on 5/2/23.
 */
public interface CrudService<T, ID> {
    /**
     * Saves a given entity. Use the returned instance for further operations as the save operation might have changed the
     * entity instance completely.
     *
     * @param entity must not be {@literal null}
     * @return {@link Future} emitting the saved entity
     * @throws IllegalArgumentException in case the given {@literal entity} is {@literal null}
     */
    @McpToolInfo(destructiveHint = true, idempotentHint = true)
    Future<T> save(T entity);

    /**
     * Saves a given entity and waits for the changes to be visible in search results before returning.
     * Use this when you need read-your-write consistency.
     *
     * @param entity must not be {@literal null}
     * @return {@link Future} emitting the saved entity after it is searchable
     * @throws IllegalArgumentException in case the given {@literal entity} is {@literal null}
     */
    @McpToolInfo(destructiveHint = true, idempotentHint = true)
    Future<T> saveSync(T entity);

    /**
     * Retrieves an entity by its id.
     *
     * @param id must not be {@literal null}
     * @return {@link Future} emitting the entity with the given id or {@link Future} emitting null if none found
     * @throws IllegalArgumentException in case the given {@literal id} is {@literal null}
     */
    @McpToolInfo(readOnlyHint = true)
    Future<T> findById(ID id);

    /**
     * Returns the number of entities available.
     *
     * @return {@link Future} emitting the number of entities
     */
    @McpToolInfo(readOnlyHint = true)
    Future<Long> count();

    /**
     * Deletes the entity with the given id.
     *
     * @param id must not be {@literal null}
     * @return {@link Future} signaling when operation has completed
     * @throws IllegalArgumentException in case the given {@literal id} is {@literal null}
     */
    @McpToolInfo(destructiveHint = true, idempotentHint = true)
    Future<Void> deleteById(ID id);

    /**
     * Deletes the entity with the given id and waits for the deletion to be visible in search results
     * before returning. Use this when you need read-your-write consistency.
     *
     * @param id must not be {@literal null}
     * @return {@link Future} signaling when the deletion is visible to search
     * @throws IllegalArgumentException in case the given {@literal id} is {@literal null}
     */
    @McpToolInfo(destructiveHint = true, idempotentHint = true)
    Future<Void> deleteByIdSync(ID id);

    /**
     * Returns a {@link Page} of entities meeting the paging restriction provided in the {@link Pageable} object.
     *
     * @param pageable the page settings to be used
     * @return a page of entities
     */
    @McpToolInfo(readOnlyHint = true)
    Future<Page<T>> findAll(Pageable pageable);

    /**
     * Returns a {@link Page} of entities matching the search text and paging restriction provided in the {@link Pageable} object.
     *
     * @param searchText the text to search for entities for
     * @param pageable the page settings to be used
     * @return a page of entities
     */
    @McpToolInfo(readOnlyHint = true)
    Future<Page<T>> search(String searchText, Pageable pageable);

    /**
     * Forces a refresh of the underlying index, making all recent writes immediately available for search.
     * This should only be used in test or batch-load scenarios.
     *
     * @return {@link Future} signaling when the index has been refreshed
     */
    @McpToolInfo(idempotentHint = true)
    Future<Void> syncIndex();
}
