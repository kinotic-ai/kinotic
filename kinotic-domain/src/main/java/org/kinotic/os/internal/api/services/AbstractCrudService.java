package org.kinotic.os.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.AuthScopeType;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.os.api.model.OrganizationScoped;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 4/24/23.
 */
@RequiredArgsConstructor
public abstract class AbstractCrudService<T extends Identifiable<String>> implements IdentifiableCrudService<T, String> {

    private static final String ORGANIZATION_ID_FIELD = "organizationId";

    protected final String indexName;
    protected final Class<T> type;
    protected final ElasticsearchAsyncClient esAsyncClient;
    protected final CrudServiceTemplate crudServiceTemplate;
    protected final SecurityContext securityContext;

    private boolean organizationScoped;

    @PostConstruct
    public void verifyIndexExists() {
        this.organizationScoped = OrganizationScoped.class.isAssignableFrom(type);
        crudServiceTemplate.verifyIndexExists(indexName);
    }

    private boolean shouldEnforceOrgScope() {
        return organizationScoped && !securityContext.isElevatedAccess();
    }

    @Override
    public CompletableFuture<Long> count() {
        if (shouldEnforceOrgScope()) {
            String orgId = requireOrganizationId();
            return crudServiceTemplate.count(indexName, b -> b.routing(orgId).query(buildOrgFilterQuery(orgId)));
        }
        return crudServiceTemplate.count(indexName, null);
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        if (shouldEnforceOrgScope()) {
            String orgId = requireOrganizationId();
            return crudServiceTemplate.findById(indexName, id, type, b -> b.routing(orgId))
                                      .thenCompose(value -> {
                                          if (value == null) {
                                              return CompletableFuture.completedFuture(null);
                                          }
                                          if (!orgId.equals(((OrganizationScoped<?>) value).getOrganizationId())) {
                                              return CompletableFuture.failedFuture(
                                                      new AuthorizationException(
                                                              "Cannot delete " + type.getSimpleName()
                                                              + " '" + id + "' owned by another organization"));
                                          }
                                          return crudServiceTemplate.deleteById(indexName, id, b -> b.routing(orgId))
                                                                    .thenApply(response -> null);
                                      });
        }
        String routing = getRoutingKeyFromId(id);
        return crudServiceTemplate.deleteById(indexName, id, routing != null ? b -> b.routing(routing) : null)
                                  .thenApply(response -> null);
    }

    @Override
    public CompletableFuture<Page<T>> findAll(Pageable pageable) {
        if (shouldEnforceOrgScope()) {
            String orgId = requireOrganizationId();
            return crudServiceTemplate.search(indexName, pageable, type,
                                              b -> b.routing(orgId).query(buildOrgFilterQuery(orgId)));
        }
        return crudServiceTemplate.search(indexName, pageable, type, null);
    }

    @Override
    public CompletableFuture<T> findById(String id) {
        if (shouldEnforceOrgScope()) {
            String orgId = requireOrganizationId();
            return crudServiceTemplate.findById(indexName, id, type, b -> b.routing(orgId))
                                      .thenApply(value -> {
                                          if (value == null) {
                                              return null;
                                          }
                                          if (!orgId.equals(((OrganizationScoped<?>) value).getOrganizationId())) {
                                              return null;
                                          }
                                          return value;
                                      });
        }
        String routing = getRoutingKeyFromId(id);
        return crudServiceTemplate.findById(indexName, id, type,
                                            routing != null ? b -> b.routing(routing) : null);
    }

    @Override
    public CompletableFuture<T> save(T value) {
        if (shouldEnforceOrgScope()) {
            enforceOrgOnSave(value);
        }
        String routing = getObjectRoutingKey(value);
        return crudServiceTemplate.save(indexName, value.getId(), value,
                                        routing != null ? b -> b.routing(routing) : null)
                                  .thenApply(indexResponse -> value);
    }

    @Override
    public CompletableFuture<Page<T>> search(String searchText, Pageable pageable) {
        if (shouldEnforceOrgScope()) {
            String orgId = requireOrganizationId();
            return crudServiceTemplate.search(indexName, pageable, type,
                                              b -> b.routing(orgId).query(buildOrgFilterQueryWithSearch(orgId, searchText)));
        }
        return crudServiceTemplate.search(indexName, pageable, type, builder -> builder.q(searchText));
    }

    @Override
    public CompletableFuture<Void> syncIndex() {
        return esAsyncClient.indices()
                            .refresh(b -> b.index(indexName))
                            .thenApply(unused -> null);
    }

    @Override
    public CompletableFuture<T> saveSync(T value) {
        if (shouldEnforceOrgScope()) {
            enforceOrgOnSave(value);
        }
        String routing = getObjectRoutingKey(value);
        return crudServiceTemplate.saveSync(indexName, value.getId(), value,
                                            routing != null ? b -> b.routing(routing) : null)
                                  .thenApply(indexResponse -> value);
    }

    /**
     * Returns the organization id to use for filtering and routing if org-scope enforcement
     * is active (object is {@link OrganizationScoped} and elevated access is not set), or
     * {@code null} if enforcement should be skipped.
     * <p>
     * Subclasses with custom query methods that call {@code crudServiceTemplate} directly
     * should call this at the top of the method and conditionally add the org filter + routing
     * when the return value is non-null.
     */
    protected String getOrganizationIdIfEnforced() {
        if (shouldEnforceOrgScope()) {
            return requireOrganizationId();
        }
        return null;
    }

    protected String getRoutingKeyFromId(String id) {
        return null;
    }

    /**
     * Ensures the current participant is authenticated under the ORGANIZATION auth scope
     * and returns the organization id to use for filtering. Thin delegate to
     * {@link SecurityContext#requireAuthScope(AuthScopeType)} — kept on the base class
     * so subclasses don't need to reach into {@code securityContext} for the common case.
     */
    protected String requireOrganizationId() {
        return securityContext.requireAuthScope(AuthScopeType.ORGANIZATION);
    }

    /**
     * Returns the organization id from the object for use as a routing key on writes.
     * This is always available after {@link #enforceOrgOnSave} has run or when the object
     * was created with the organization id already set.
     */
    private String getObjectRoutingKey(T value) {
        if (organizationScoped) {
            String orgId = ((OrganizationScoped<?>) value).getOrganizationId();
            if (orgId != null && !orgId.isBlank()) {
                return orgId;
            }
        }
        return null;
    }

    /**
     * Autopopulates or validates the organization id on the object before a save. When the field is unset it is
     * populated with the participant's organization id; when set it must equal the participant's organization id.
     */
    @SuppressWarnings("unchecked") // safe because we know the object is OrganizationScoped
    private void enforceOrgOnSave(T value) {
        String orgId = requireOrganizationId();
        OrganizationScoped<String> scoped = (OrganizationScoped<String>) value;
        String entityOrgId = scoped.getOrganizationId();

        Validate.notBlank(entityOrgId, "Organization id must be set on " + type.getSimpleName());

        if (!orgId.equals(entityOrgId)) {
            throw new AuthorizationException(
                    "Cannot save " + type.getSimpleName()
                    + " with organizationId '" + entityOrgId
                    + "' while authenticated as organization '" + orgId + "'");
        }
    }

    private Query buildOrgFilterQuery(String orgId) {
        return Query.of(q -> q.bool(b -> b.filter(fq -> fq.term(t -> t.field(ORGANIZATION_ID_FIELD)
                                                                      .value(orgId)))));
    }

    private Query buildOrgFilterQueryWithSearch(String orgId, String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            return buildOrgFilterQuery(orgId);
        }
        return Query.of(q -> q.bool(b -> b.must(m -> m.queryString(qs -> qs.query(searchText).analyzeWildcard(true)))
                                          .filter(fq -> fq.term(t -> t.field(ORGANIZATION_ID_FIELD)
                                                                      .value(orgId)))));
    }

}
