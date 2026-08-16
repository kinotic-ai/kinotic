package org.kinotic.domain.internal.api.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.OrganizationScoped;
import org.kinotic.domain.api.model.security.ApplicationParticipant;
import org.kinotic.domain.api.model.security.OrganizationParticipant;
import org.kinotic.domain.internal.api.repositories.AbstractOrganizationScopedRepository;

/**
 * Service base that adds organization-scope enforcement on top of an
 * {@link AbstractOrganizationScopedRepository}. Per call this class derives the organization
 * id — from the participant's auth scope on reads and deletes, or from the entity itself on
 * writes — and forwards it to the repository, whose orgId-aware overloads scope routing,
 * document id, and read filters.
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
    public Future<Long> count() {
        return scopedRepository.count(requireOrganizationId());
    }

    @Override
    public Future<T> findById(String id) {
        return scopedRepository.findById(id, requireOrganizationId());
    }

    @Override
    public Future<Void> deleteById(String id) {
        return beforeDelete(id).compose(v -> scopedRepository.deleteById(id, requireOrganizationId()));
    }

    @Override
    public Future<Void> deleteByIdSync(String id) {
        return beforeDelete(id).compose(v -> scopedRepository.deleteByIdSync(id, requireOrganizationId()));
    }

    /**
     * Hook run before every delete — {@link #deleteById} and {@link #deleteByIdSync} both
     * call it, so a subclass cannot accidentally guard one delete path and not the other.
     * Override to validate the delete or cascade dependent data; the org-scoped delete
     * proceeds when the returned future completes.
     */
    protected Future<Void> beforeDelete(String id) {
        return Future.succeededFuture();
    }

    @Override
    public Future<Page<T>> findAll(Pageable pageable) {
        return scopedRepository.findAll(requireOrganizationId(), pageable);
    }

    @Override
    public Future<T> save(T value) {
        return beforeSave(value).compose(v -> {
            enforceOrgOnSave(value);
            return scopedRepository.save(value, resolveWriteOrgId(value));
        });
    }

    @Override
    public Future<T> saveSync(T value) {
        return beforeSave(value).compose(v -> {
            enforceOrgOnSave(value);
            return scopedRepository.saveSync(value, resolveWriteOrgId(value));
        });
    }

    @Override
    public Future<T> create(T value) {
        return beforeSave(value).compose(v -> {
            enforceOrgOnSave(value);
            return scopedRepository.create(value, resolveWriteOrgId(value));
        });
    }

    @Override
    public Future<T> createSync(T value) {
        return beforeSave(value).compose(v -> {
            enforceOrgOnSave(value);
            return scopedRepository.createSync(value, resolveWriteOrgId(value));
        });
    }

    /**
     * Hook run before every write — {@link #save}, {@link #saveSync}, {@link #create}, and
     * {@link #createSync} all call it, so a subclass cannot accidentally guard one write
     * path and not the others. Override to validate and prepare the entity (defaults, ids,
     * timestamps); org enforcement and the write proceed when the returned future completes.
     */
    protected Future<Void> beforeSave(T entity) {
        return Future.succeededFuture();
    }

    @Override
    public Future<Page<T>> search(String searchText, Pageable pageable) {
        return scopedRepository.search(searchText, requireOrganizationId(), pageable);
    }

    @Override
    public Future<Void> syncIndex() {
        return scopedRepository.syncIndex();
    }

    /**
     * Returns the {@code organizationId} of the current {@link OrganizationParticipant}.
     * {@link ApplicationParticipant} is a sibling type, not a subtype, so application-scoped
     * callers are rejected — this check is what keeps org-scoped services closed to applications.
     *
     * @throws IllegalStateException if no participant is bound to the current context
     * @throws AuthorizationException if the participant is not an {@code OrganizationParticipant}
     */
    protected String requireOrganizationId() {
        return securityContext.requireParticipant(OrganizationParticipant.class).getOrganizationId();
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
