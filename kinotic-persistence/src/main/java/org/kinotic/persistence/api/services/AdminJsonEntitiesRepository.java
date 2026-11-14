package org.kinotic.persistence.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.RawJson;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.persistence.api.model.FastestType;
import org.kinotic.persistence.api.model.QueryParameter;
import org.kinotic.persistence.api.model.TenantSpecificId;

import java.util.List;

/**
 * Provides Admin access to entities for a given {@link EntityDefinition}.
 * Admin access allows you to read and write data for tenants other than the one that is logged into by the participant.
 * Created by Nic Padilla 🤪on 6/18/23.
 *
 * Every function is checked on the entity definition named, for the permission of its rows the function needs,
 * the one {@link JsonEntitiesRepository} checks for the same function: reading, listing, counting, querying
 * and searching need {@code can_read}, and deleting {@code can_delete}. Deleting implies updating, and
 * updating reading. The check is made on the definition itself for every caller, since a function here acts on
 * the rows of the tenants it names: a tenant's user holding the permission within its tenant alone is refused,
 * and one granted it on the definition or on the application selects any tenant.
 */
@Publish
@Zone(DomainUtil.APP_API_ZONE)
@AuthzResource(value = AuthzConstants.ENTITY_DEFINITION_TYPE, parent = AuthzConstants.APPLICATION_TYPE, acrossTenants = true)
public interface AdminJsonEntitiesRepository {

    /**
     * Returns the number of entities available.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to count
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @param participant the participant of the logged-in user
     * @return {@link Future} emitting the number of entities.
     */
    @AuthzCheck(resourceId = "{entityDefinitionId}")
    Future<Long> count(String entityDefinitionId, List<String> tenantSelection, ScopedParticipant participant);

    /**
     * Returns the number of entities available for the given query.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to count. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param query       the query used to limit result
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @param participant the participant of the logged-in user
     * @return {@link Future} emitting the number of entities.
     */
    @AuthzCheck(resourceId = "{entityDefinitionId}")
    Future<Long> countByQuery(String entityDefinitionId, String query, List<String> tenantSelection, ScopedParticipant participant);

    /**
     * Deletes the entity with the given id.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to delete the entity for
     * @param id          must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link Future} emitting when delete is complete
     */
    @AuthzCheck(permission = AuthzConstants.CAN_DELETE, resourceId = "{entityDefinitionId}")
    Future<Void> deleteById(String entityDefinitionId, TenantSpecificId id, ScopedParticipant participant);

    /**
     * Deletes any entities that match the given query.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to delete the entity for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param query       the query used to filter records to delete, must not be {@literal null}
     * @param tenantSelection the list of tenants to use when deleting entities by the given query
     * @param participant the participant of the logged-in user
     * @return {@link Future} emitting when delete is complete
     */
    @AuthzCheck(permission = AuthzConstants.CAN_DELETE, resourceId = "{entityDefinitionId}")
    Future<Void> deleteByQuery(String entityDefinitionId, String query, List<String> tenantSelection, ScopedParticipant participant);

    /**
     * Returns a {@link Page} of entities meeting the paging restriction provided in the {@code Pageable} object.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to find the entity for
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @param pageable    the page settings to be used
     * @param participant the participant of the logged-in user
     * @return a page of entities
     */
    @AuthzCheck(resourceId = "{entityDefinitionId}")
    Future<Page<FastestType>> findAll(String entityDefinitionId, List<String> tenantSelection, Pageable pageable, ScopedParticipant participant);

    /**
     * Retrieves an entity by its id.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to find the entity for
     * @param id          must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link Future} with the entity with the given id or {@link Future} emitting null if none found
     */
    @AuthzCheck(resourceId = "{entityDefinitionId}")
    Future<FastestType> findById(String entityDefinitionId, TenantSpecificId id, ScopedParticipant participant);

    /**
     * Retrieves a list of entities by their id.
     *
     * @param entityDefinitionId the id of the {@link EntityDefinition} to find the entity for. (this is the {@link EntityDefinition#getApplicationId()} + "." + {@link EntityDefinition#getName()})
     * @param ids         must not be {@literal null}
     * @param participant the participant of the logged-in user
     * @return {@link Future} with the list of matched entities with the given ids or {@link Future} emitting an empty list if none found
     */
    @AuthzCheck(resourceId = "{entityDefinitionId}")
    Future<List<FastestType>> findByIds(String entityDefinitionId, List<TenantSpecificId> ids, ScopedParticipant participant);

    /**
     * Executes a named query.
     * The caller needs {@code can_read} on the definition for a query that reads rows, {@code can_edit} for one
     * that inserts or updates them, and {@code can_delete} for one that deletes them.
     *
     * @param entityDefinitionId     the id of the {@link EntityDefinition} that this named query is defined for
     * @param queryName       the name of {@link FunctionDefinition} that defines the query
     * @param queryParameters the parameters to pass to the query
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @param participant     the participant of the logged-in user
     * @return {@link Future} with the result of the query
     */
    @AuthzCheck(permissionResolver = AuthzConstants.NAMED_QUERY_PERMISSION_RESOLVER, resourceId = "{entityDefinitionId}")
    Future<List<RawJson>> namedQuery(String entityDefinitionId,
                                     String queryName,
                                     List<QueryParameter> queryParameters,
                                     List<String> tenantSelection,
                                     ScopedParticipant participant);

    /**
     * Executes a named query and returns a {@link Page} of results.
     * The caller needs {@code can_read} on the definition for a query that reads rows, {@code can_edit} for one
     * that inserts or updates them, and {@code can_delete} for one that deletes them.
     *
     * @param entityDefinitionId     the id of the {@link EntityDefinition} that this named query is defined for
     * @param queryName       the name of {@link FunctionDefinition} that defines the query
     * @param queryParameters the parameters to pass to the query
     * @param tenantSelection the list of tenants to use when retrieving the entity records
     * @param pageable        the page settings to be useds
     * @param participant     the participant of the logged-in user
     * @return {@link Future} with the result of the query
     */
    @AuthzCheck(permissionResolver = AuthzConstants.NAMED_QUERY_PERMISSION_RESOLVER, resourceId = "{entityDefinitionId}")
    Future<Page<RawJson>> namedQueryPage(String entityDefinitionId,
                                         String queryName,
                                         List<QueryParameter> queryParameters,
                                         List<String> tenantSelection,
                                         Pageable pageable,
                                         ScopedParticipant participant);

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
     * @return a {@link Future} of a page of entities
     */
    @AuthzCheck(resourceId = "{entityDefinitionId}")
    Future<Page<FastestType>> search(String entityDefinitionId, String searchText, List<String> tenantSelection, Pageable pageable, ScopedParticipant participant);

}
