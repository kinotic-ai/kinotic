package org.kinotic.persistence.api.services;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.model.FastestType;
import org.kinotic.persistence.api.model.QueryParameter;
import org.kinotic.os.api.model.RawJson;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.security.Participant;
import tools.jackson.databind.util.TokenBuffer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provides access to entities for a given EntityDefinition.
 * Created by Nic Padilla 🤪on 6/18/23.
 */
@Publish
public interface JsonEntitiesService {

    /**
     * Updates all given entities, this gives an opportunity to perform partial updates of the data EntityDefinition.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to save the entities for
     * @param entities    all the entities to save
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} that will complete when all entities have been saved
     */
    CompletableFuture<Void> bulkSave(String entityDefinitionId, TokenBuffer entities, Participant participant);

    /**
     * Saves all given entities.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to update the entities for
     * @param entities    all the entities to save
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} that will complete when all entities have been saved
     */
    CompletableFuture<Void> bulkUpdate(String entityDefinitionId, TokenBuffer entities, Participant participant);

    /**
     * Returns the number of entities available.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to count
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} emitting the number of entities.
     */
    CompletableFuture<Long> count(String entityDefinitionId, Participant participant);

    /**
     * Returns the number of entities available for the given query.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to count. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param query       the query used to limit result
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} emitting the number of entities.
     */
    CompletableFuture<Long> countByQuery(String entityDefinitionId, String query, Participant participant);

    /**
     * Deletes the entity with the given id.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to delete the entity for
     * @param id          must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} emitting when delete is complete
     */
    CompletableFuture<Void> deleteById(String entityDefinitionId, String id, Participant participant);

    /**
     * Deletes any entities that match the given query.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to delete the entity for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param query       the query used to filter records to delete, must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} emitting when delete is complete
     */
    CompletableFuture<Void> deleteByQuery(String entityDefinitionId, String query, Participant participant);

    /**
     * Returns a {@link Page} of entities meeting the paging restriction provided in the {@code Pageable} object.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to find the entity for
     * @param pageable    the page settings to be used
     * @param participant the participant of the logged-in user
     * @return a page of entities
     */
    CompletableFuture<Page<FastestType>> findAll(String entityDefinitionId, Pageable pageable, Participant participant);

    /**
     * Retrieves an entity by its id.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to find the entity for
     * @param id          must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} with the entity with the given id or {@link CompletableFuture} emitting null if none found
     */
    CompletableFuture<FastestType> findById(String entityDefinitionId, String id, Participant participant);

    /**
     * Retrieves a list of entities by their id.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to find the entity for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param ids         must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} with the list of matched entities with the given ids or {@link CompletableFuture} emitting an empty list if none found
     */
    CompletableFuture<List<FastestType>> findByIds(String entityDefinitionId, List<String> ids, Participant participant);

    /**
     * Executes a named query.
     *
     * @param entityDefinitionId     the id of the {@link EntityDefinition} that this named query is defined for
     * @param queryName       the name of {@link FunctionDefinition} that defines the query
     * @param queryParameters the parameters to pass to the query
     * @param participant     the participant of the logged-in user
     * @return {@link CompletableFuture} with the result of the query
     */
    CompletableFuture<List<RawJson>> namedQuery(String entityDefinitionId,
                                                String queryName,
                                                List<QueryParameter> queryParameters,
                                                Participant participant);

    /**
     * Executes a named query and returns a {@link Page} of results.
     *
     * @param entityDefinitionId     the id of the {@link EntityDefinition} that this named query is defined for
     * @param queryName       the name of {@link FunctionDefinition} that defines the query
     * @param queryParameters the parameters to pass to the query
     * @param pageable        the page settings to be useds
     * @param participant     the participant of the logged-in user
     * @return {@link CompletableFuture} with the result of the query
     */
    CompletableFuture<Page<RawJson>> namedQueryPage(String entityDefinitionId,
                                                    String queryName,
                                                    List<QueryParameter> queryParameters,
                                                    Pageable pageable,
                                                    Participant participant);

    /**
     * Saves a given entity. Use the returned instance for further operations as the save operation might have changed the
     * entity instance completely.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to save the entity for
     * @param entity      must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} emitting the saved entity
     */
    CompletableFuture<TokenBuffer> save(String entityDefinitionId, TokenBuffer entity, Participant participant);

    /**
     * Returns a {@link Page} of entities matching the search text and paging restriction provided in the {@code Pageable} object.
     * <p>
     * You can find more information about the search syntax <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#query-string-syntax">here</a>
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to search
     * @param searchText  the text to search for entities for
     * @param pageable    the page settings to be used
     * @param participant the participant of the logged-in user
     * @return a {@link CompletableFuture} of a page of entities
     */
    CompletableFuture<Page<FastestType>> search(String entityDefinitionId, String searchText, Pageable pageable, Participant participant);

    /**
     * This operation makes all the recent writes immediately available for search.
     * @param entityDefinitionId the id of the {@link EntityDefinition} to sync the index for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param participant     the participant of the logged-in user
     * @return a {@link CompletableFuture} that will complete when the operation is complete
     */
    CompletableFuture<Void> syncIndex(String entityDefinitionId, Participant participant);

    /**
     * Updates a given entity. This will only override the fields that are present in the given entity.
     * If any fields are not present in the given entity data they will not be changed.
     * If the entity does not exist it will be created.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to update the entity for
     * @param entity      must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} emitting the saved entity
     */
    CompletableFuture<TokenBuffer> update(String entityDefinitionId, TokenBuffer entity, Participant participant);

}
