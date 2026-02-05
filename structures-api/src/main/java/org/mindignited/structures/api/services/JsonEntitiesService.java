package org.mindignited.structures.api.services;

import tools.jackson.databind.util.TokenBuffer;
import org.kinotic.continuum.api.annotations.Proxy;
import org.kinotic.continuum.api.annotations.Publish;
import org.kinotic.continuum.api.security.Participant;
import org.kinotic.continuum.core.api.crud.Page;
import org.kinotic.continuum.core.api.crud.Pageable;
import org.kinotic.continuum.idl.api.schema.FunctionDefinition;
import org.mindignited.structures.api.domain.FastestType;
import org.mindignited.structures.api.domain.QueryParameter;
import org.mindignited.structures.api.domain.RawJson;
import org.mindignited.structures.api.domain.Structure;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provides access to entities for a given structure.
 * Created by Nic Padilla 🤪on 6/18/23.
 */
@Publish
@Proxy
public interface JsonEntitiesService {

    /**
     * Updates all given entities, this gives an opportunity to perform partial updates of the data structure.
     *
     * @param structureId the id of the structure to save the entities for
     * @param entities    all the entities to save
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} that will complete when all entities have been saved
     */
    CompletableFuture<Void> bulkSave(String structureId, TokenBuffer entities, Participant participant);

    /**
     * Saves all given entities.
     *
     * @param structureId the id of the structure to update the entities for
     * @param entities    all the entities to save
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} that will complete when all entities have been saved
     */
    CompletableFuture<Void> bulkUpdate(String structureId, TokenBuffer entities, Participant participant);

    /**
     * Returns the number of entities available.
     *
     * @param structureId the id of the structure to count
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} emitting the number of entities.
     */
    CompletableFuture<Long> count(String structureId, Participant participant);

    /**
     * Returns the number of entities available for the given query.
     *
     * @param structureId the id of the structure to count. (this is the {@link Structure#getApplicationId()} + "." + {@link Structure#getName()})
     * @param query       the query used to limit result
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} emitting the number of entities.
     */
    CompletableFuture<Long> countByQuery(String structureId, String query, Participant participant);

    /**
     * Deletes the entity with the given id.
     *
     * @param structureId the id of the structure to delete the entity for
     * @param id          must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} emitting when delete is complete
     */
    CompletableFuture<Void> deleteById(String structureId, String id, Participant participant);

    /**
     * Deletes any entities that match the given query.
     *
     * @param structureId the id of the structure to delete the entity for. (this is the {@link Structure#getApplicationId()} + "." + {@link Structure#getName()})
     * @param query       the query used to filter records to delete, must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} emitting when delete is complete
     */
    CompletableFuture<Void> deleteByQuery(String structureId, String query, Participant participant);

    /**
     * Returns a {@link Page} of entities meeting the paging restriction provided in the {@code Pageable} object.
     *
     * @param structureId the id of the structure to find the entity for
     * @param pageable    the page settings to be used
     * @param participant the participant of the logged-in user
     * @return a page of entities
     */
    CompletableFuture<Page<FastestType>> findAll(String structureId, Pageable pageable, Participant participant);

    /**
     * Retrieves an entity by its id.
     *
     * @param structureId the id of the structure to find the entity for
     * @param id          must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} with the entity with the given id or {@link CompletableFuture} emitting null if none found
     */
    CompletableFuture<FastestType> findById(String structureId, String id, Participant participant);

    /**
     * Retrieves a list of entities by their id.
     *
     * @param structureId the id of the structure to find the entity for. (this is the {@link Structure#getApplicationId()} + "." + {@link Structure#getName()})
     * @param ids         must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} with the list of matched entities with the given ids or {@link CompletableFuture} emitting an empty list if none found
     */
    CompletableFuture<List<FastestType>> findByIds(String structureId, List<String> ids, Participant participant);

    /**
     * Executes a named query.
     *
     * @param structureId     the id of the structure that this named query is defined for
     * @param queryName       the name of {@link FunctionDefinition} that defines the query
     * @param queryParameters the parameters to pass to the query
     * @param participant     the participant of the logged-in user
     * @return {@link CompletableFuture} with the result of the query
     */
    CompletableFuture<List<RawJson>> namedQuery(String structureId,
                                                String queryName,
                                                List<QueryParameter> queryParameters,
                                                Participant participant);

    /**
     * Executes a named query and returns a {@link Page} of results.
     *
     * @param structureId     the id of the structure that this named query is defined for
     * @param queryName       the name of {@link FunctionDefinition} that defines the query
     * @param queryParameters the parameters to pass to the query
     * @param pageable        the page settings to be useds
     * @param participant     the participant of the logged-in user
     * @return {@link CompletableFuture} with the result of the query
     */
    CompletableFuture<Page<RawJson>> namedQueryPage(String structureId,
                                                    String queryName,
                                                    List<QueryParameter> queryParameters,
                                                    Pageable pageable,
                                                    Participant participant);

    /**
     * Saves a given entity. Use the returned instance for further operations as the save operation might have changed the
     * entity instance completely.
     *
     * @param structureId the id of the structure to save the entity for
     * @param entity      must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} emitting the saved entity
     */
    CompletableFuture<TokenBuffer> save(String structureId, TokenBuffer entity, Participant participant);

    /**
     * Returns a {@link Page} of entities matching the search text and paging restriction provided in the {@code Pageable} object.
     * <p>
     * You can find more information about the search syntax <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#query-string-syntax">here</a>
     *
     * @param structureId the id of the structure to search
     * @param searchText  the text to search for entities for
     * @param pageable    the page settings to be used
     * @param participant the participant of the logged-in user
     * @return a {@link CompletableFuture} of a page of entities
     */
    CompletableFuture<Page<FastestType>> search(String structureId, String searchText, Pageable pageable, Participant participant);

    /**
     * This operation makes all the recent writes immediately available for search.
     * @param structureId the id of the structure to sync the index for. (this is the {@link Structure#getApplicationId()} + "." + {@link Structure#getName()})
     * @param participant     the participant of the logged-in user
     * @return a {@link CompletableFuture} that will complete when the operation is complete
     */
    CompletableFuture<Void> syncIndex(String structureId, Participant participant);

    /**
     * Updates a given entity. This will only override the fields that are present in the given entity.
     * If any fields are not present in the given entity data they will not be changed.
     * If the entity does not exist it will be created.
     *
     * @param structureId the id of the structure to update the entity for
     * @param entity      must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} emitting the saved entity
     */
    CompletableFuture<TokenBuffer> update(String structureId, TokenBuffer entity, Participant participant);

}
