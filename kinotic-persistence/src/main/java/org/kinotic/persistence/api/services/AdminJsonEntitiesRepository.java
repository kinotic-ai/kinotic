package org.kinotic.persistence.api.services;

import org.kinotic.os.api.model.RawJson;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.model.*;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.security.Participant;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provides Admin access to entities for a given {@link EntityDefinition}.
 * Admin access allows you to read and write data for tenants other than the one that is logged into by the participant.
 * Created by Nic Padilla 🤪on 6/18/23.
 */
@Publish
public interface AdminJsonEntitiesRepository {

    /**
     * Returns the number of entities available.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to count
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} emitting the number of entities.
     */
    CompletableFuture<Long> count(String entityDefinitionId, List<String> tenantSelection, Participant participant);

    /**
     * Returns the number of entities available for the given query.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to count. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param query       the query used to limit result
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} emitting the number of entities.
     */
    CompletableFuture<Long> countByQuery(String entityDefinitionId, String query, List<String> tenantSelection, Participant participant);

    /**
     * Deletes the entity with the given id.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to delete the entity for
     * @param id          must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} emitting when delete is complete
     */
    CompletableFuture<Void> deleteById(String entityDefinitionId, TenantSpecificId id, Participant participant);

    /**
     * Deletes any entities that match the given query.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to delete the entity for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param query       the query used to filter records to delete, must not be {@literal null}
     * @param tenantSelection the list of tenants to use when deleting entities by the given query
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} emitting when delete is complete
     */
    CompletableFuture<Void> deleteByQuery(String entityDefinitionId, String query, List<String> tenantSelection, Participant participant);

    /**
     * Returns a {@link Page} of entities meeting the paging restriction provided in the {@code Pageable} object.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to find the entity for
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @param pageable    the page settings to be used
     * @param participant the participant of the logged-in user
     * @return a page of entities
     */
    CompletableFuture<Page<FastestType>> findAll(String entityDefinitionId, List<String> tenantSelection, Pageable pageable, Participant participant);

    /**
     * Retrieves an entity by its id.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to find the entity for
     * @param id          must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} with the entity with the given id or {@link CompletableFuture} emitting null if none found
     */
    CompletableFuture<FastestType> findById(String entityDefinitionId, TenantSpecificId id, Participant participant);

    /**
     * Retrieves a list of entities by their id.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to find the entity for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param ids         must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link CompletableFuture} with the list of matched entities with the given ids or {@link CompletableFuture} emitting an empty list if none found
     */
    CompletableFuture<List<FastestType>> findByIds(String entityDefinitionId, List<TenantSpecificId> ids, Participant participant);

    /**
     * Executes a named query.
     *
     * @param entityDefinitionId     the id of the {@link EntityDefinition} that this named query is defined for
     * @param queryName       the name of {@link FunctionDefinition} that defines the query
     * @param queryParameters the parameters to pass to the query
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @param participant     the participant of the logged-in user
     * @return {@link CompletableFuture} with the result of the query
     */
    CompletableFuture<List<RawJson>> namedQuery(String entityDefinitionId,
                                                String queryName,
                                                List<QueryParameter> queryParameters,
                                                List<String> tenantSelection,
                                                Participant participant);

    /**
     * Executes a named query and returns a {@link Page} of results.
     *
     * @param entityDefinitionId     the id of the {@link EntityDefinition} that this named query is defined for
     * @param queryName       the name of {@link FunctionDefinition} that defines the query
     * @param queryParameters the parameters to pass to the query
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @param pageable        the page settings to be useds
     * @param participant     the participant of the logged-in user
     * @return {@link CompletableFuture} with the result of the query
     */
    CompletableFuture<Page<RawJson>> namedQueryPage(String entityDefinitionId,
                                                    String queryName,
                                                    List<QueryParameter> queryParameters,
                                                    List<String> tenantSelection,
                                                    Pageable pageable,
                                                    Participant participant);

    /**
     * Returns a {@link Page} of entities matching the search text and paging restriction provided in the {@code Pageable} object.
     * <p>
     * You can find more information about the search syntax <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#query-string-syntax">here</a>
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to search
     * @param searchText  the text to search for entities for
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @param pageable    the page settings to be used
     * @param participant the participant of the logged-in user
     * @return a {@link CompletableFuture} of a page of entities
     */
    CompletableFuture<Page<FastestType>> search(String entityDefinitionId, String searchText, List<String> tenantSelection, Pageable pageable, Participant participant);

}
