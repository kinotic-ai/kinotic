package org.kinotic.domain.internal.api.services;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.AuthScopeType;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.OrganizationScoped;
import org.kinotic.domain.internal.api.repositories.AbstractOrganizationScopedRepository;

import java.util.concurrent.CompletableFuture;

/**
 * Service base that adds organization-scope enforcement on top of an
 * {@link AbstractOrganizationScopedRepository}. Per call this class decides whether to pass
 * the participant's organization id to the repository (which uses it as routing and, for
 * read queries, as a filter), validates the org id on write, or steps out of the way when
 * {@link SecurityContext#isElevatedAccess()} is set.
 * <p>
 * The repository's {@code orgId} overloads reject null, so this class explicitly branches
 * between the orgId-aware and the unscoped repository overloads.
 */
public abstract class AbstractOrganizationScopedService<T extends OrganizationScoped<String>>
        extends AbstractCrudService<T> {

    protected final AbstractOrganizationScopedRepository<T> scopedRepository;
    protected final SecurityContext securityContext;

    public AbstractOrganizationScopedService(AbstractOrganizationScopedRepository<T> repository,
                                             SecurityContext securityContext) {
        super(repository);
        this.scopedRepository = repository;
        this.securityContext = securityContext;
    }

    @Override
    public CompletableFuture<Long> count() {
        String orgId = getOrganizationIdIfEnforced();
        return orgId != null ? scopedRepository.count(orgId) : scopedRepository.count();
    }

    @Override
    public CompletableFuture<T> findById(String id) {
        String orgId = getOrganizationIdIfEnforced();
        if (orgId == null) orgId = getRoutingKeyFromId(id);
        return orgId != null
                ? scopedRepository.findById(id, orgId)
                : scopedRepository.findById(id);
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        String orgId = getOrganizationIdIfEnforced();
        if (orgId == null) orgId = getRoutingKeyFromId(id);
        return orgId != null
                ? scopedRepository.deleteById(id, orgId)
                : scopedRepository.deleteById(id);
    }

    @Override
    public CompletableFuture<Page<T>> findAll(Pageable pageable) {
        String orgId = getOrganizationIdIfEnforced();
        return orgId != null
                ? scopedRepository.findAll(pageable, orgId)
                : scopedRepository.findAll(pageable);
    }

    @Override
    public CompletableFuture<T> save(T value) {
        if (!securityContext.isElevatedAccess()) {
            enforceOrgOnSave(value);
        }
        String routing = getObjectRoutingKey(value);
        return routing != null ? scopedRepository.save(value, routing) : scopedRepository.save(value);
    }

    @Override
    public CompletableFuture<T> saveSync(T value) {
        if (!securityContext.isElevatedAccess()) {
            enforceOrgOnSave(value);
        }
        String routing = getObjectRoutingKey(value);
        return routing != null ? scopedRepository.saveSync(value, routing) : scopedRepository.saveSync(value);
    }

    @Override
    public CompletableFuture<Page<T>> search(String searchText, Pageable pageable) {
        String orgId = getOrganizationIdIfEnforced();
        return orgId != null
                ? scopedRepository.search(searchText, pageable, orgId)
                : scopedRepository.search(searchText, pageable);
    }

    /**
     * Returns the organization id to use for filtering and routing if org-scope enforcement
     * is active (elevated access is not set), or {@code null} if enforcement should be skipped.
     * Subclasses with custom finders branch on this and pick the repository overload
     * accordingly.
     */
    protected String getOrganizationIdIfEnforced() {
        return securityContext.isElevatedAccess() ? null : requireOrganizationId();
    }

    /**
     * Ensures the current participant is authenticated under the ORGANIZATION auth scope and
     * returns their organization id. Thin delegate to
     * {@link SecurityContext#requireAuthScope(AuthScopeType)}.
     */
    protected String requireOrganizationId() {
        return securityContext.requireAuthScope(AuthScopeType.ORGANIZATION);
    }

    /**
     * Override point for services whose ids carry a routing prefix. Returns the routing key
     * to use for {@code findById}/{@code deleteById} when org-scope enforcement is off (and
     * therefore the participant's orgId isn't available). Default implementation returns
     * {@code null}, leaving Elasticsearch to hash the id.
     */
    protected String getRoutingKeyFromId(String id) {
        return null;
    }

    /**
     * Returns the routing key derived from an entity, used on {@code save}/{@code saveSync}.
     * For org-scoped entities this is the organization id when present.
     */
    protected String getObjectRoutingKey(T value) {
        String orgId = value.getOrganizationId();
        return (orgId != null && !orgId.isBlank()) ? orgId : null;
    }

    /**
     * Autopopulates or validates the organization id on the object before a save. The field
     * must be set and must equal the participant's organization id.
     */
    private void enforceOrgOnSave(T value) {
        String orgId = requireOrganizationId();
        String entityOrgId = value.getOrganizationId();

        Validate.notBlank(entityOrgId, "Organization id must be set on " + scopedRepository.getType().getSimpleName());

        if (!orgId.equals(entityOrgId)) {
            throw new AuthorizationException(
                    "Cannot save " + scopedRepository.getType().getSimpleName()
                    + " with organizationId '" + entityOrgId
                    + "' while authenticated as organization '" + orgId + "'");
        }
    }
}
