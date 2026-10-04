package org.kinotic.persistence.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.model.FastestType;
import org.kinotic.persistence.api.model.QueryParameter;
import org.kinotic.domain.api.model.RawJson;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import tools.jackson.databind.util.TokenBuffer;

import java.util.List;

/**
 * Provides access to entities for a given EntityDefinition.
 * Created by Nic Padilla 🤪on 6/18/23.
 *
 * Every function is checked on the caller's tenant, or on the application for a caller with none, for the
 * permission of the definition's rows it needs: reading one needs {@code can_read}, listing, counting, querying
 * and searching {@code can_search}, saving {@code can_create}, updating {@code can_edit} and deleting
 * {@code can_delete}, each named for the definition, as {@code invoice_can_read} is.
 */
@Publish
@Zone(DomainUtil.APP_API_ZONE)
@AuthzResource(value = "{entityDefinitionId}", parent = AuthzUtil.TENANT_TYPE)
public interface JsonEntitiesRepository {

    /**
     * Updates all given entities; this gives an opportunity to perform partial updates of the data EntityDefinition.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to save the entities for
     * @param entities    all the entities to save
     * @param participant the participant of the logged-in user
     * @return {@link Future} that will complete when all entities have been saved
     */
    @AuthzCheck(resource = AuthzUtil.TENANT_TYPE, permission = "can_create")
    Future<Void> bulkSave(String entityDefinitionId, TokenBuffer entities, ScopedParticipant participant);

    /**
     * Saves all given entities.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to update the entities for
     * @param entities    all the entities to save
     * @param participant the participant of the logged-in user
     * @return {@link Future} that will complete when all entities have been saved
     */
    @AuthzCheck(resource = AuthzUtil.TENANT_TYPE, permission = "can_edit")
    Future<Void> bulkUpdate(String entityDefinitionId, TokenBuffer entities, ScopedParticipant participant);

    /**
     * Returns the number of entities available.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to count
     * @param participant the participant of the logged-in user
     * @return {@link Future} emitting the number of entities.
     */
    @AuthzCheck(resource = AuthzUtil.TENANT_TYPE, permission = "can_search")
    Future<Long> count(String entityDefinitionId, ScopedParticipant participant);

    /**
     * Returns the number of entities available for the given query.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to count. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param query       the query used to limit results
     * @param participant the participant of the logged-in user
     * @return {@link Future} emitting the number of entities.
     */
    @AuthzCheck(resource = AuthzUtil.TENANT_TYPE, permission = "can_search")
    Future<Long> countByQuery(String entityDefinitionId, String query, ScopedParticipant participant);

    /**
     * Deletes the entity with the given id.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to delete the entity for
     * @param id          must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link Future} emitting when delete is complete
     */
    @AuthzCheck(resource = AuthzUtil.TENANT_TYPE, permission = "can_delete")
    Future<Void> deleteById(String entityDefinitionId, String id, ScopedParticipant participant);

    /**
     * Deletes any entities that match the given query.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to delete the entity for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param query       the query used to filter records to delete, must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link Future} emitting when delete is complete
     */
    @AuthzCheck(resource = AuthzUtil.TENANT_TYPE, permission = "can_delete")
    Future<Void> deleteByQuery(String entityDefinitionId, String query, ScopedParticipant participant);

    /**
     * Returns a {@link Page} of entities meeting the paging restriction provided in the {@code Pageable} object.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to find the entity for
     * @param pageable    the page settings to be used
     * @param participant the participant of the logged-in user
     * @return a page of entities
     */
    @AuthzCheck(resource = AuthzUtil.TENANT_TYPE, permission = "can_search")
    Future<Page<FastestType>> findAll(String entityDefinitionId, Pageable pageable, ScopedParticipant participant);

    /**
     * Retrieves an entity by its id.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to find the entity for
     * @param id          must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link Future} with the entity with the given id or {@link Future} emitting null if none found
     */
    @AuthzCheck(resource = AuthzUtil.TENANT_TYPE, permission = "can_read")
    Future<FastestType> findById(String entityDefinitionId, String id, ScopedParticipant participant);

    /**
     * Retrieves a list of entities by their id.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to find the entity for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param ids         must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link Future} with the list of matched entities with the given ids or {@link Future} emitting an empty list if none found
     */
    @AuthzCheck(resource = AuthzUtil.TENANT_TYPE, permission = "can_read")
    Future<List<FastestType>> findByIds(String entityDefinitionId, List<String> ids, ScopedParticipant participant);

    /**
     * Executes a named query.
     *
     * @param entityDefinitionId     the id of the {@link EntityDefinition} that this named query is defined for
     * @param queryName       the name of {@link FunctionDefinition} that defines the query
     * @param queryParameters the parameters to pass to the query
     * @param participant     the participant of the logged-in user
     * @return {@link Future} with the result of the query
     */
    @AuthzCheck(resource = AuthzUtil.TENANT_TYPE, permission = "can_search")
    Future<List<RawJson>> namedQuery(String entityDefinitionId,
                                     String queryName,
                                     List<QueryParameter> queryParameters,
                                     ScopedParticipant participant);

    /**
     * Executes a named query and returns a {@link Page} of results.
     *
     * @param entityDefinitionId     the id of the {@link EntityDefinition} that this named query is defined for
     * @param queryName       the name of {@link FunctionDefinition} that defines the query
     * @param queryParameters the parameters to pass to the query
     * @param pageable        the page settings to be useds
     * @param participant     the participant of the logged-in user
     * @return {@link Future} with the result of the query
     */
    @AuthzCheck(resource = AuthzUtil.TENANT_TYPE, permission = "can_search")
    Future<Page<RawJson>> namedQueryPage(String entityDefinitionId,
                                         String queryName,
                                         List<QueryParameter> queryParameters,
                                         Pageable pageable,
                                         ScopedParticipant participant);

    /**
     * Saves a given entity. Use the returned instance for further operations as the save operation might have changed the
     * entity instance completely.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to save the entity for
     * @param entity      must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link Future} emitting the saved entity
     */
    @AuthzCheck(resource = AuthzUtil.TENANT_TYPE, permission = "can_create")
    Future<TokenBuffer> save(String entityDefinitionId, TokenBuffer entity, ScopedParticipant participant);

    /**
     * Returns a {@link Page} of entities matching the search text and paging restriction provided in the {@code Pageable} object.
     * <p>
     * You can find more information about the search syntax <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#query-string-syntax">here</a>
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to search
     * @param searchText  the text to search for entities for
     * @param pageable    the page settings to be used
     * @param participant the participant of the logged-in user
     * @return a {@link Future} of a page of entities
     */
    @AuthzCheck(resource = AuthzUtil.TENANT_TYPE, permission = "can_search")
    Future<Page<FastestType>> search(String entityDefinitionId, String searchText, Pageable pageable, ScopedParticipant participant);

    /**
     * This operation makes all the recent writes immediately available for search.
     * @param entityDefinitionId the id of the {@link EntityDefinition} to sync the index for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param participant     the participant of the logged-in user
     * @return a {@link Future} that will complete when the operation is complete
     */
    @AuthzCheck(resource = AuthzUtil.APPLICATION_TYPE, objectId = "{@applicationId}", permission = "can_edit")
    Future<Void> syncIndex(String entityDefinitionId, ScopedParticipant participant);

    /**
     * Updates a given entity. This will only override the fields that are present in the given entity.
     * If any fields are not present in the given entity data they will not be changed.
     * If the entity does not exist it will be created.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to update the entity for
     * @param entity      must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link Future} emitting the saved entity
     */
    @AuthzCheck(resource = AuthzUtil.TENANT_TYPE, permission = "can_edit")
    Future<TokenBuffer> update(String entityDefinitionId, TokenBuffer entity, ScopedParticipant participant);

}
