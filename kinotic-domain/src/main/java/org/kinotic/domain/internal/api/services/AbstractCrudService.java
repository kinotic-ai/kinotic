package org.kinotic.domain.internal.api.services;

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
import org.kinotic.domain.api.model.OrganizationScoped;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;

import java.util.concurrent.CompletableFuture;

/**
 * Service base that adds organization-scope enforcement on top of an {@link AbstractRepository}.
 * Pure persistence lives on the repository; this class composes one and decides &mdash; per call &mdash;
 * whether to inject the participant's organization id as a routing key and filter, validate it on
 * write, or step out of the way when {@code SecurityContext.isElevatedAccess()} is set.
 * <p>
 * Created by Nav&iacute;d Mitchell &#x1f92a; on 4/24/23.
 */
@RequiredArgsConstructor
public abstract class AbstractCrudService<T extends Identifiable<String>> implements IdentifiableCrudService<T, String> {

    private static final String ORGANIZATION_ID_FIELD = "organizationId";

    protected final AbstractRepository<T> repository;
    protected final SecurityContext securityContext;

    private boolean organizationScoped;

    @PostConstruct
    public void initOrganizationScoped() {
        this.organizationScoped = OrganizationScoped.class.isAssignableFrom(repository.getType());
    }

    private boolean shouldEnforceOrgScope() {
        return organizationScoped && !securityContext.isElevatedAccess();
    }

    @Override
    public CompletableFuture<Long> count() {
        if (shouldEnforceOrgScope()) {
            String orgId = requireOrganizationId();
            Query filter = buildOrgFilterQuery(orgId);
            return repository.count(b -> b.routing(orgId).query(filter));
        }
        return repository.count();
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        if (shouldEnforceOrgScope()) {
            String orgId = requireOrganizationId();
            return repository.findById(id, b -> b.routing(orgId))
                             .thenCompose(value -> {
                                 if (value == null) {
                                     return CompletableFuture.completedFuture(null);
                                 }
                                 if (!orgId.equals(((OrganizationScoped<?>) value).getOrganizationId())) {
                                     return CompletableFuture.failedFuture(
                                             new AuthorizationException(
                                                     "Cannot delete " + repository.getType().getSimpleName()
                                                     + " '" + id + "' owned by another organization"));
                                 }
                                 return repository.deleteById(id, b -> b.routing(orgId));
                             });
        }
        return repository.deleteById(id);
    }

    @Override
    public CompletableFuture<Page<T>> findAll(Pageable pageable) {
        if (shouldEnforceOrgScope()) {
            String orgId = requireOrganizationId();
            Query filter = buildOrgFilterQuery(orgId);
            return repository.findAll(pageable, b -> b.routing(orgId).query(filter));
        }
        return repository.findAll(pageable);
    }

    @Override
    public CompletableFuture<T> findById(String id) {
        if (shouldEnforceOrgScope()) {
            String orgId = requireOrganizationId();
            return repository.findById(id, b -> b.routing(orgId))
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
        return repository.findById(id);
    }

    @Override
    public CompletableFuture<T> save(T value) {
        if (shouldEnforceOrgScope()) {
            enforceOrgOnSave(value);
        }
        return repository.save(value);
    }

    @Override
    public CompletableFuture<T> saveSync(T value) {
        if (shouldEnforceOrgScope()) {
            enforceOrgOnSave(value);
        }
        return repository.saveSync(value);
    }

    @Override
    public CompletableFuture<Page<T>> search(String searchText, Pageable pageable) {
        if (shouldEnforceOrgScope()) {
            String orgId = requireOrganizationId();
            Query filter = buildOrgFilterQueryWithSearch(orgId, searchText);
            return repository.findAll(pageable, b -> b.routing(orgId).query(filter));
        }
        return repository.search(searchText, pageable);
    }

    @Override
    public CompletableFuture<Void> syncIndex() {
        return repository.syncIndex();
    }

    /**
     * Returns the organization id to use for filtering and routing if org-scope enforcement
     * is active (object is {@link OrganizationScoped} and elevated access is not set), or
     * {@code null} if enforcement should be skipped.
     * <p>
     * Subclasses with custom query methods that call into the repository directly should
     * call this at the top of the method and, when the result is non-null, attach the
     * routing and an {@link #buildOrgFilterQuery org filter} through the repository's
     * consumer-style overloads.
     */
    protected String getOrganizationIdIfEnforced() {
        if (shouldEnforceOrgScope()) {
            return requireOrganizationId();
        }
        return null;
    }

    /**
     * Ensures the current participant is authenticated under the ORGANIZATION auth scope
     * and returns the organization id to use for filtering. Thin delegate to
     * {@link SecurityContext#requireAuthScope(AuthScopeType)} &mdash; kept on the base class
     * so subclasses don't need to reach into {@code securityContext} for the common case.
     */
    protected String requireOrganizationId() {
        return securityContext.requireAuthScope(AuthScopeType.ORGANIZATION);
    }

    /**
     * Builds a term-filter query restricting results to the given organization id. Exposed
     * to subclasses that compose their own queries and need to AND in the org constraint.
     */
    protected Query buildOrgFilterQuery(String orgId) {
        return Query.of(q -> q.bool(b -> b.filter(fq -> fq.term(t -> t.field(ORGANIZATION_ID_FIELD)
                                                                      .value(orgId)))));
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

        Validate.notBlank(entityOrgId, "Organization id must be set on " + repository.getType().getSimpleName());

        if (!orgId.equals(entityOrgId)) {
            throw new AuthorizationException(
                    "Cannot save " + repository.getType().getSimpleName()
                    + " with organizationId '" + entityOrgId
                    + "' while authenticated as organization '" + orgId + "'");
        }
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
