package org.kinotic.domain.internal.api.services;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.IdentifiableCrudService;
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
 * {@link AbstractOrganizationScopedRepository}. Per call this class derives the organization
 * id (from the participant's auth scope, or from the entity on writes) and forwards it to
 * the repository, whose orgId-aware overloads scope routing, document id, and read filters.
 * <p>
 * The repository deliberately exposes no unscoped CRUD overloads — under its composite
 * {@code orgId + "-" + id} document id, a lookup without {@code orgId} cannot address the
 * right document. When elevated access is set and no orgId can be derived from the security
 * context or the id itself ({@link #getRoutingKeyFromId(String)}), this service fails fast
 * rather than issuing a lookup that would silently miss.
 */
public abstract class AbstractOrganizationScopedService<T extends OrganizationScoped<String>>
        implements IdentifiableCrudService<T, String> {

    protected final AbstractOrganizationScopedRepository<T> scopedRepository;
    protected final SecurityContext securityContext;

    public AbstractOrganizationScopedService(AbstractOrganizationScopedRepository<T> repository,
                                             SecurityContext securityContext) {
        this.scopedRepository = repository;
        this.securityContext = securityContext;
    }

    @Override
    public CompletableFuture<Long> count() {
        return scopedRepository.count(resolveReadOrgId(null));
    }

    @Override
    public CompletableFuture<T> findById(String id) {
        return scopedRepository.findById(id, resolveReadOrgId(id));
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        return scopedRepository.deleteById(id, resolveReadOrgId(id));
    }

    @Override
    public CompletableFuture<Page<T>> findAll(Pageable pageable) {
        return scopedRepository.findAll(resolveReadOrgId(null), pageable);
    }

    @Override
    public CompletableFuture<T> save(T value) {
        if (!securityContext.isElevatedAccess()) {
            enforceOrgOnSave(value);
        }
        return scopedRepository.save(value, resolveWriteOrgId(value));
    }

    @Override
    public CompletableFuture<T> saveSync(T value) {
        if (!securityContext.isElevatedAccess()) {
            enforceOrgOnSave(value);
        }
        return scopedRepository.saveSync(value, resolveWriteOrgId(value));
    }

    @Override
    public CompletableFuture<Page<T>> search(String searchText, Pageable pageable) {
        return scopedRepository.search(searchText, resolveReadOrgId(null), pageable);
    }

    @Override
    public CompletableFuture<Void> syncIndex() {
        return scopedRepository.syncIndex();
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
     * Override point for services whose ids carry an org routing prefix. When the participant
     * is operating under elevated access (no org context from the security context), the
     * service uses this hook to recover the orgId from {@code id} itself so it can still
     * address the composite document id. Default returns {@code null}; subclasses with
     * id-based routing override it.
     * <p>
     * FIXME: remove when elevated access is removed. This hook only exists to recover an
     * orgId for elevated-access lookups; once every caller carries an org context, the
     * orgId-aware repository methods can be used directly.
     */
    protected String getRoutingKeyFromId(String id) {
        return null;
    }

    /**
     * Resolves the orgId for a read or delete. Uses the participant's org if enforcement is
     * active, otherwise falls back to {@link #getRoutingKeyFromId(String)}. Throws when
     * neither is available — under composite document ids a read without an orgId can't
     * address any document.
     */
    private String resolveReadOrgId(String id) {
        String orgId = getOrganizationIdIfEnforced();
        if (orgId != null) return orgId;
        if (id != null) {
            String fromId = getRoutingKeyFromId(id);
            if (fromId != null) return fromId;
        }
        throw new IllegalStateException(
                "Elevated-access operation on " + scopedRepository.getType().getSimpleName()
                + " requires an organization id, but none could be derived"
                + (id != null ? " from id '" + id + "'" : ""));
    }

    private String resolveWriteOrgId(T value) {
        String orgId = value.getOrganizationId();
        Validate.notBlank(orgId,
                          "Organization id must be set on %s before save",
                          scopedRepository.getType().getSimpleName());
        return orgId;
    }

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
