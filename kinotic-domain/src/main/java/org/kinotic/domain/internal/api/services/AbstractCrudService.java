package org.kinotic.domain.internal.api.services;

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
 * whether to pass the participant's organization id (which the repository uses as routing and,
 * for read queries, as a filter), validate it on write, or step out of the way when
 * {@code SecurityContext.isElevatedAccess()} is set.
 * <p>
 * Created by Nav&iacute;d Mitchell &#x1f92a; on 4/24/23.
 */
@RequiredArgsConstructor
public abstract class AbstractCrudService<T extends Identifiable<String>> implements IdentifiableCrudService<T, String> {

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
        return repository.count(getOrganizationIdIfEnforced());
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        String orgId = getOrganizationIdIfEnforced();
        if (orgId == null) {
            return repository.deleteById(id, getRoutingKeyFromId(id));
        }
        return repository.findById(id, orgId)
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
                             return repository.deleteById(id, orgId);
                         });
    }

    @Override
    public CompletableFuture<Page<T>> findAll(Pageable pageable) {
        return repository.findAll(pageable, getOrganizationIdIfEnforced());
    }

    @Override
    public CompletableFuture<T> findById(String id) {
        String orgId = getOrganizationIdIfEnforced();
        String routing = orgId != null ? orgId : getRoutingKeyFromId(id);
        return repository.findById(id, routing)
                         .thenApply(value -> {
                             if (value == null) return null;
                             if (orgId != null
                                     && !orgId.equals(((OrganizationScoped<?>) value).getOrganizationId())) {
                                 return null;
                             }
                             return value;
                         });
    }

    @Override
    public CompletableFuture<T> save(T value) {
        if (shouldEnforceOrgScope()) {
            enforceOrgOnSave(value);
        }
        return repository.save(value, getObjectRoutingKey(value));
    }

    @Override
    public CompletableFuture<T> saveSync(T value) {
        if (shouldEnforceOrgScope()) {
            enforceOrgOnSave(value);
        }
        return repository.saveSync(value, getObjectRoutingKey(value));
    }

    @Override
    public CompletableFuture<Page<T>> search(String searchText, Pageable pageable) {
        return repository.search(searchText, pageable, getOrganizationIdIfEnforced());
    }

    @Override
    public CompletableFuture<Void> syncIndex() {
        return repository.syncIndex();
    }

    /**
     * Returns the organization id to use for filtering and routing if org-scope enforcement
     * is active (object is {@link OrganizationScoped} and elevated access is not set), or
     * {@code null} if enforcement should be skipped. Subclasses with custom finders pass the
     * result straight to the corresponding repository overload.
     */
    protected String getOrganizationIdIfEnforced() {
        return shouldEnforceOrgScope() ? requireOrganizationId() : null;
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
     * For {@link OrganizationScoped} entities this is the organization id; otherwise
     * {@code null}.
     */
    protected String getObjectRoutingKey(T value) {
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

        Validate.notBlank(entityOrgId, "Organization id must be set on " + repository.getType().getSimpleName());

        if (!orgId.equals(entityOrgId)) {
            throw new AuthorizationException(
                    "Cannot save " + repository.getType().getSimpleName()
                    + " with organizationId '" + entityOrgId
                    + "' while authenticated as organization '" + orgId + "'");
        }
    }

}
