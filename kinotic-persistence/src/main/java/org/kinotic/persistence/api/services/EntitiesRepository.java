package org.kinotic.persistence.api.services;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.TenantSpecificId;
import org.kinotic.persistence.api.model.ParameterHolder;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provides access to entities for a given {@link EntityDefinition}.
 * Created by Navíd Mitchell 🤪on 5/10/23.
 */
public interface EntitiesRepository {

    /**
     * Saves all given entities.
     * @param entityDefinitionId the id of the {@link EntityDefinition} to save the entities for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param entities    all the entities to save
     * @param context     the context for this operation
     * @return {@link CompletableFuture} that will complete when all entities have been saved
     */
    <T> CompletableFuture<Void> bulkSave(String entityDefinitionId, T entities, EntityContext context);

    /**
     * Updates all given entities.
     * @param entityDefinitionId the id of the {@link EntityDefinition} to update the entities for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param entities    all the entities to save
     * @param context     the context for this operation
     * @return {@link CompletableFuture} that will complete when all entities have been saved
     */
    <T> CompletableFuture<Void> bulkUpdate(String entityDefinitionId, T entities, EntityContext context);

    /**
     * Returns the number of entities available.
     * @param entityDefinitionId the id of the {@link EntityDefinition} to count. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param context     the context for this operation
     * @return {@link CompletableFuture} emitting the number of entities.
     */
    CompletableFuture<Long> count(String entityDefinitionId, EntityContext context);

    /**
     * Returns the number of entities available for the given query.
     * @param entityDefinitionId the id of the {@link EntityDefinition} to count. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param query       the query used to limit result
     * @param context     the context for this operation
     * @return {@link CompletableFuture} emitting the number of entities.
     */
    CompletableFuture<Long> countByQuery(String entityDefinitionId, String query, EntityContext context);

    /**
     * Deletes the entity with the given id.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to delete the entity for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param id          must not be {@literal null}
     * @param context     the context for this operation
     * @return {@link CompletableFuture} emitting when delete is complete
     */
    CompletableFuture<Void> deleteById(String entityDefinitionId, String id, EntityContext context);

    /**
     * Deletes the entity with the given id.
     * NOTE: this method is only allowed if the {@link EntityDefinition#isMultiTenantSelectionEnabled()} is true
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to delete the entity for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param id          must not be {@literal null}
     * @param context     the context for this operation
     * @return {@link CompletableFuture} emitting when delete is complete
     */
    CompletableFuture<Void> deleteById(String entityDefinitionId, TenantSpecificId id, EntityContext context);

    /**
     * Deletes any entities that match the given query.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to delete the entity for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param query       the query used to filter records to delete, must not be {@literal null}
     * @param context     the context for this operation
     * @return {@link CompletableFuture} emitting when delete is complete
     */
    CompletableFuture<Void> deleteByQuery(String entityDefinitionId, String query, EntityContext context);

    /**
     * Returns a {@link Page} of entities meeting the paging restriction provided in the {@code Pageable} object.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to find the entity for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param pageable    the page settings to be used
     * @param type        the type of the entity
     * @param context     the context for this operation
     * @return a page of entities
     */
    <T> CompletableFuture<Page<T>> findAll(String entityDefinitionId, Pageable pageable, Class<T> type, EntityContext context);

    /**
     * Retrieves an entity by its id.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to find the entity for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param id          must not be {@literal null}
     * @param type        the type of the entity
     * @param context     the context for this operation
     * @return {@link CompletableFuture} with the entity with the given id or {@link CompletableFuture} emitting null if none found
     */
    <T> CompletableFuture<T> findById(String entityDefinitionId, String id, Class<T> type, EntityContext context);

    /**
     * Retrieves an entity by its id.
     * NOTE: this method is only allowed if the {@link EntityDefinition#isMultiTenantSelectionEnabled()} is true
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to find the entity for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param id          must not be {@literal null}
     * @param type        the type of the entity
     * @param context     the context for this operation
     * @return {@link CompletableFuture} with the entity with the given id or {@link CompletableFuture} emitting null if none found
     */
    <T> CompletableFuture<T> findById(String entityDefinitionId, TenantSpecificId id, Class<T> type, EntityContext context);

    /**
     * Retrieves a list of entities by their id.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to find the entity for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param ids         must not be {@literal null}
     * @param type        the type of the entity
     * @param context     the context for this operation
     * @return {@link CompletableFuture} with the list of matched entities with the given ids or {@link CompletableFuture} emitting an empty list if none found
     */
    <T> CompletableFuture<List<T>> findByIds(String entityDefinitionId, List<String> ids, Class<T> type, EntityContext context);

    /**
     * Retrieves a list of entities by their id.
     * NOTE: this method is only allowed if the {@link EntityDefinition#isMultiTenantSelectionEnabled()} is true
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to find the entity for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param ids         must not be {@literal null}
     * @param type        the type of the entity
     * @param context     the context for this operation
     * @return {@link CompletableFuture} with the list of matched entities with the given ids or {@link CompletableFuture} emitting an empty list if none found
     */
    <T> CompletableFuture<List<T>> findByIdsWithTenant(String entityDefinitionId, List<TenantSpecificId> ids, Class<T> type, EntityContext context);

    /**
     * Executes a named query.
     *
     * @param entityDefinitionId     the id of the {@link EntityDefinition} that this named query is defined for
     * @param queryName       the name of {@link FunctionDefinition} that defines the query
     * @param parameterHolder the parameters to pass to the query
     * @param type            the type of the entity
     * @param context         the context for this operation
     * @return {@link CompletableFuture} with the result of the query
     */
    <T> CompletableFuture<List<T>> namedQuery(String entityDefinitionId,
                                              String queryName,
                                              ParameterHolder parameterHolder,
                                              Class<T> type,
                                              EntityContext context);

    /**
     * Executes a named query and returns a {@link Page} of results.
     *
     * @param entityDefinitionId     the id of the {@link EntityDefinition} that this named query is defined for
     * @param queryName       the name of {@link FunctionDefinition} that defines the query
     * @param parameterHolder the parameters to pass to the query
     * @param pageable        the page settings to be used
     * @param type            the type of the entity
     * @param context         the context for this operation
     * @return {@link CompletableFuture} with the result of the query
     */
    <T> CompletableFuture<Page<T>> namedQueryPage(String entityDefinitionId,
                                                  String queryName,
                                                  ParameterHolder parameterHolder,
                                                  Pageable pageable,
                                                  Class<T> type,
                                                  EntityContext context);

    /**
     * This operation makes all the recent writes immediately available for search.
     * @param entityDefinitionId the id of the {@link EntityDefinition} to sync the index for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param context     the context for this operation
     * @return a {@link CompletableFuture} that will complete when the operation is complete
     */
    CompletableFuture<Void> syncIndex(String entityDefinitionId, EntityContext context);

    /**
     * Saves a given entity. This will override all data if there is an existing entity with the same id.
     * Use the returned instance for further operations as the save operation might have changed the entity instance.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to save the entity for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param entity      must not be {@literal null}
     * @param context     the context for this operation
     * @return {@link CompletableFuture} emitting the saved entity
     */
    <T> CompletableFuture<T> save(String entityDefinitionId, T entity, EntityContext context);

    /**
     * Returns a {@link Page} of entities matching the search text and paging restriction provided in the {@code Pageable} object.
     * <p>
     * You can find more information about the search syntax <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#query-string-syntax">here</a>
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to search. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param searchText  the text to search for entitiess
     * @param pageable    the page settings to be used
     * @param type        the type of the entity
     * @param context     the context for this operation
     * @return a {@link CompletableFuture} of a page of entities
     */
    <T> CompletableFuture<Page<T>> search(String entityDefinitionId, String searchText, Pageable pageable, Class<T> type, EntityContext context);

    /**
     * Updates a given entity. This will only override the fields that are present in the given entity.
     * If any fields are not present in the given entity data they will not be changed.
     * If the entity does not exist it will be created.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to update the entity for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param entity      must not be {@literal null}
     * @param context     the context for this operation
     * @return {@link CompletableFuture} emitting the saved entity
     */
    <T> CompletableFuture<T> update(String entityDefinitionId, T entity, EntityContext context);

}
