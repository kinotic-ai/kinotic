package org.mindignited.structures.internal.api.services;

import org.mindignited.continuum.core.api.crud.Page;
import org.mindignited.continuum.core.api.crud.Pageable;
import org.mindignited.continuum.idl.api.schema.FunctionDefinition;
import org.mindignited.structures.api.domain.EntityContext;
import org.mindignited.structures.api.domain.RawJson;
import org.mindignited.structures.api.domain.Structure;
import org.mindignited.structures.api.domain.TenantSpecificId;
import org.mindignited.structures.api.domain.ParameterHolder;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provides functionality to query data for a single "Entity" for a given {@link Structure}.
 * Created by Navíd Mitchell 🤪 on 5/2/23.
 */
public interface EntityService {

    /**
     * Saves all given entities.
     * @param entities all the entities to save
     * @param context the context for this operation
     * @return {@link CompletableFuture} that will complete when all entities have been saved
     * @param <T> the type of the entities, this can be a {@link List} or {@link RawJson}
     */
    <T> CompletableFuture<Void> bulkSave(T entities, EntityContext context);

    /**
     * Updates all given entities.
     * @param entities all the entities to save
     * @param context the context for this operation
     * @return {@link CompletableFuture} that will complete when all entities have been saved
     * @param <T> the type of the entities, this can be a {@link List} or {@link RawJson}
     */
    <T> CompletableFuture<Void> bulkUpdate(T entities, EntityContext context);

    /**
     * Returns the number of entities available.
     *
     * @param context the context for this operation
     * @return {@link CompletableFuture} emitting the number of entities
     */
    CompletableFuture<Long> count(EntityContext context);

    /**
     * Returns the number of entities available for the given query.
     * @param query       the query used to limit result
     * @param context     the context for this operation
     * @return {@link CompletableFuture} emitting the number of entities.
     */
    CompletableFuture<Long> countByQuery(String query, EntityContext context);

    /**
     * Deletes the entity with the given id.
     *
     * @param id      must not be {@literal null}
     * @param context the context for this operation
     * @return {@link CompletableFuture} signaling when operation has completed
     */
    CompletableFuture<Void> deleteById(String id, EntityContext context);

    /**
     * Deletes the entity with the given id.
     * NOTE: this method is only allowed if the {@link Structure#isMultiTenantSelectionEnabled()} is true
     *
     * @param id      must not be {@literal null}
     * @param context the context for this operation
     * @return {@link CompletableFuture} signaling when operation has completed
     */
    CompletableFuture<Void> deleteById(TenantSpecificId id, EntityContext context);

    /**
     * Deletes any entities that match the given query.
     *
     * @param query       the query used to filter records to delete, must not be {@literal null}
     * @param context     the context for this operation
     * @return {@link CompletableFuture} emitting when delete is complete
     */
    CompletableFuture<Void> deleteByQuery(String query, EntityContext context);

    /**
     * Returns a {@link Page} of entities meeting the paging restriction provided in the {@link Pageable} object.
     *
     * @param pageable the page settings to be used
     * @param type     the type of the entity
     * @param context  the context for this operation
     * @return a page of entities
     */
    <T> CompletableFuture<Page<T>> findAll(Pageable pageable, Class<T> type, EntityContext context);

    /**
     * Retrieves an entity by its id.
     *
     * @param id      must not be {@literal null}
     * @param type    the type of the entity
     * @param context the context for this operation
     * @return {@link CompletableFuture} emitting the entity with the given id or {@link CompletableFuture} emitting null if none found
     */
    <T> CompletableFuture<T> findById(String id, Class<T> type, EntityContext context);

    /**
     * Retrieves an entity by its id.
     * NOTE: this method is only allowed if the {@link Structure#isMultiTenantSelectionEnabled()} is true
     *
     * @param id      must not be {@literal null}
     * @param type    the type of the entity
     * @param context the context for this operation
     * @return {@link CompletableFuture} emitting the entity with the given id or {@link CompletableFuture} emitting null if none found
     */
    <T> CompletableFuture<T> findById(TenantSpecificId id, Class<T> type, EntityContext context);

    /**
     * Retrieves a list of entities by their id.
     *
     * @param ids         must not be {@literal null}
     * @param type        the type of the entity
     * @param context     the context for this operation
     * @return {@link CompletableFuture} with the list of matched entities with the given ids or {@link CompletableFuture} emitting null if none found
     */
    <T> CompletableFuture<List<T>> findByIds(List<String> ids, Class<T> type, EntityContext context);

    /**
     * Retrieves a list of entities by their id.
     * NOTE: this method is only allowed if the {@link Structure#isMultiTenantSelectionEnabled()} is true
     *
     * @param ids         must not be {@literal null}
     * @param type        the type of the entity
     * @param context     the context for this operation
     * @return {@link CompletableFuture} with the list of matched entities with the given ids or {@link CompletableFuture} emitting null if none found
     */
    <T> CompletableFuture<List<T>> findByIdsWithTenant(List<TenantSpecificId> ids, Class<T> type, EntityContext context);

    /**
     * Executes a named query.
     *
     * @param queryName       the name of {@link FunctionDefinition} that defines the query
     * @param parameterHolder the parameters to pass to the query
     * @param type            the type of the entity
     * @param context         the context for this operation
     * @return {@link CompletableFuture} with the result of the query
     */
    <T> CompletableFuture<List<T>> namedQuery(String queryName,
                                              ParameterHolder parameterHolder,
                                              Class<T> type,
                                              EntityContext context);

    /**
     * Executes a named query and returns a {@link Page} of results.
     *
     * @param queryName       the name of {@link FunctionDefinition} that defines the query
     * @param parameterHolder the parameters to pass to the query
     * @param pageable        the page settings to be used
     * @param type            the type of the entity
     * @param context         the context for this operation
     * @return {@link CompletableFuture} with the result of the query
     */
    <T> CompletableFuture<Page<T>> namedQueryPage(String queryName,
                                                  ParameterHolder parameterHolder,
                                                  Pageable pageable,
                                                  Class<T> type,
                                                  EntityContext context);

    /**
     * Saves a given entity. This will override all data if there is an existing entity with the same id.
     * Use the returned instance for further operations as the save operation might have changed the entity instance.
     *
     * @param entity  must not be {@literal null}
     * @param context the context for this operation
     * @return {@link CompletableFuture} emitting the saved entity
     */
    <T> CompletableFuture<T> save(T entity, EntityContext context);

    /**
     * Returns a {@link Page} of entities matching the search text and paging restriction provided in the {@link Pageable} object.
     *
     * @param searchText the text to search for entities for
     * @param pageable   the page settings to be used
     * @param type       the type of the entity
     * @param context    the context for this operation
     * @return a page of entities
     */
    <T> CompletableFuture<Page<T>> search(String searchText, Pageable pageable, Class<T> type, EntityContext context);

    /**
     * This operation makes all the recent writes immediately available for search.
     * @param context the context for this operation
     * @return a {@link CompletableFuture} that will complete when the operation is complete
     */
    CompletableFuture<Void> syncIndex(EntityContext context);

    /**
     * Updates a given entity. This will only override the fields that are present in the given entity.
     * If any fields are not present in the given entity data they will not be changed.
     * If the entity does not exist it will be created.
     * Use the returned instance for further operations as the save operation might have changed the entity instance.
     *
     * @param entity      must not be {@literal null}
     * @param context     the context for this operation
     * @return {@link CompletableFuture} emitting the saved entity
     */
    <T> CompletableFuture<T> update(T entity, EntityContext context);

}
