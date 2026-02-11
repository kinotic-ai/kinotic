package org.kinotic.continuum.core.api.crud;

import java.util.concurrent.CompletableFuture;

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
     * @return {@link CompletableFuture} emitting the saved entity
     * @throws IllegalArgumentException in case the given {@literal entity} is {@literal null}
     */
    CompletableFuture<T> save(T entity);

    /**
     * Retrieves an entity by its id.
     *
     * @param id must not be {@literal null}
     * @return {@link CompletableFuture} emitting the entity with the given id or {@link CompletableFuture} emitting null if none found
     * @throws IllegalArgumentException in case the given {@literal id} is {@literal null}
     */
    CompletableFuture<T> findById(ID id);

    /**
     * Returns the number of entities available.
     *
     * @return {@link CompletableFuture} emitting the number of entities
     */
    CompletableFuture<Long> count();

    /**
     * Deletes the entity with the given id.
     *
     * @param id must not be {@literal null}
     * @return {@link CompletableFuture} signaling when operation has completed
     * @throws IllegalArgumentException in case the given {@literal id} is {@literal null}
     */
    CompletableFuture<Void> deleteById(ID id);

    /**
     * Returns a {@link Page} of entities meeting the paging restriction provided in the {@link Pageable} object.
     *
     * @param pageable the page settings to be used
     * @return a page of entities
     */
    CompletableFuture<Page<T>> findAll(Pageable pageable);

    /**
     * Returns a {@link Page} of entities matching the search text and paging restriction provided in the {@link Pageable} object.
     *
     * @param searchText the text to search for entities for
     * @param pageable the page settings to be used
     * @return a page of entities
     */
    CompletableFuture<Page<T>> search(String searchText, Pageable pageable);
}
