package org.kinotic.os.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantContext;
import org.kinotic.os.api.model.OrganizationScoped;
import org.kinotic.os.api.model.iam.AuthScopeType;

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
    protected final ParticipantContext participantContext;

    private boolean organizationScoped;

    @PostConstruct
    public void verifyIndexExists() {
        this.organizationScoped = OrganizationScoped.class.isAssignableFrom(type);
        crudServiceTemplate.verifyIndexExists(indexName);
    }

    @Override
    public CompletableFuture<Long> count() {
        if (organizationScoped) {
            String orgId = requireOrganizationId();
            return crudServiceTemplate.count(indexName, b -> b.routing(orgId).query(buildOrgFilterQuery(orgId)));
        }
        return crudServiceTemplate.count(indexName, null);
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        if (organizationScoped) {
            String orgId = requireOrganizationId();
            return crudServiceTemplate.findById(indexName, id, type, b -> b.routing(orgId))
                                      .thenCompose(entity -> {
                                          if (entity == null) {
                                              // Truly missing document: mirror ES DELETE semantics and complete normally.
                                              return CompletableFuture.completedFuture(null);
                                          }
                                          if (!orgId.equals(((OrganizationScoped<?>) entity).getOrganizationId())) {
                                              return CompletableFuture.failedFuture(
                                                      new AuthorizationException(
                                                              "Cannot delete " + type.getSimpleName()
                                                              + " '" + id + "' owned by another organization"));
                                          }
                                          return crudServiceTemplate.deleteById(indexName, id, b -> b.routing(orgId))
                                                                    .thenApply(response -> null);
                                      });
        }
        return crudServiceTemplate.deleteById(indexName, id, null)
                                  .thenApply(response -> null);
    }

    @Override
    public CompletableFuture<Page<T>> findAll(Pageable pageable) {
        if (organizationScoped) {
            String orgId = requireOrganizationId();
            return crudServiceTemplate.search(indexName, pageable, type,
                                              b -> b.routing(orgId).query(buildOrgFilterQuery(orgId)));
        }
        return crudServiceTemplate.search(indexName, pageable, type, null);
    }

    @Override
    public CompletableFuture<T> findById(String id) {
        if (organizationScoped) {
            String orgId = requireOrganizationId();
            return crudServiceTemplate.findById(indexName, id, type, b -> b.routing(orgId))
                                      .thenApply(entity -> {
                                          if (entity == null) {
                                              return null;
                                          }
                                          if (!orgId.equals(((OrganizationScoped<?>) entity).getOrganizationId())) {
                                              return null;
                                          }
                                          return entity;
                                      });
        }
        return crudServiceTemplate.findById(indexName, id, type, null);
    }

    @Override
    public CompletableFuture<T> save(T entity) {
        return crudServiceTemplate.save(indexName, entity.getId(), entity, null)
                                  .thenCompose(indexResponse -> findById(indexResponse.id()));
    }

    @Override
    public CompletableFuture<Page<T>> search(String searchText, Pageable pageable) {
        if (organizationScoped) {
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
    public CompletableFuture<T> saveSync(T entity) {
        return crudServiceTemplate.saveSync(indexName, entity.getId(), entity, null)
                                  .thenCompose(indexResponse -> findById(indexResponse.id()));
    }

    /**
     * Ensures the current participant is authenticated under the ORGANIZATION auth scope and returns the
     * organization id to use for filtering. This mirrors the tenant-scoping pattern in
     * {@code ReadPreProcessor#createQueryWithTenantLogic}.
     *
     * @return the organization id from the participant's auth scope
     * @throws IllegalStateException if no {@link Participant} is bound to the current Vert.x context
     * @throws AuthorizationException if the participant's auth scope type is not {@link AuthScopeType#ORGANIZATION}
     */
    private String requireOrganizationId() {
        Participant participant = participantContext.currentParticipant();
        if (participant == null) {
            throw new IllegalStateException(
                    "No Participant is bound to the current Vert.x context for "
                    + type.getSimpleName() + " operation");
        }
        if (!AuthScopeType.ORGANIZATION.name().equals(participant.getAuthScopeType())) {
            throw new AuthorizationException(
                    "Access to " + type.getSimpleName()
                    + " requires auth scope " + AuthScopeType.ORGANIZATION.name()
                    + " but was '" + participant.getAuthScopeType() + "'");
        }
        return participant.getAuthScopeId();
    }

    /**
     * Autopopulates or validates the organization id on the entity before a save. When the field is unset it is
     * populated with the participant's organization id; when set it must equal the participant's organization id.
     *
     * @return the organization id of the participant (also used as the Elasticsearch routing key for the write)
     */
    @SuppressWarnings("unchecked")
    private String enforceOrgOnSave(T entity) {
        String orgId = requireOrganizationId();
        OrganizationScoped<String> scoped = (OrganizationScoped<String>) entity;
        String entityOrgId = scoped.getOrganizationId();
        if (entityOrgId == null || entityOrgId.isBlank()) {
            scoped.setOrganizationId(orgId);
        } else if (!orgId.equals(entityOrgId)) {
            throw new AuthorizationException(
                    "Cannot save " + type.getSimpleName()
                    + " with organizationId '" + entityOrgId
                    + "' while authenticated as organization '" + orgId + "'");
        }
        return orgId;
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
