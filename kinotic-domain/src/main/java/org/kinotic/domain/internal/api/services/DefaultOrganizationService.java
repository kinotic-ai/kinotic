package org.kinotic.domain.internal.api.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.OrganizationLoginLink;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.internal.api.repositories.OrganizationRepository;
import org.kinotic.domain.internal.api.repositories.ParticipantIdentityRepository;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class DefaultOrganizationService extends AbstractCrudService<Organization> implements OrganizationService {

    private final ParticipantIdentityRepository identityRepository;
    private final EmailService emailService;
    private final RelationshipService relationships;

    public DefaultOrganizationService(OrganizationRepository repository,
                                      ParticipantIdentityRepository identityRepository,
                                      EmailService emailService,
                                      RelationshipService relationships) {
        super(repository);
        this.identityRepository = identityRepository;
        this.emailService = emailService;
        this.relationships = relationships;
    }

    @Override
    protected Future<Void> beforeSave(Organization entity) {
        Validate.notNull(entity.getName(), "Organization name cannot be null");

        if (entity.getId() == null) {
            entity.setId(DomainUtil.slugifyId(entity.getName()));
            entity.setCreated(new Date());
        }
        // Validate only; re-minting an update's id would silently write a new document
        DomainUtil.validateOrganizationId(entity.getId());

        entity.setUpdated(new Date());
        return Future.succeededFuture();
    }

    /**
     * Creates a new organization, deriving its id from the slugified name. Fails with
     * {@link AlreadyExistsException} if an organization with that name (hence id) already
     * exists, rather than overwriting it.
     */
    @Override
    public Future<Organization> create(Organization entity) {
        Validate.notBlank(entity.getName(), "Organization name cannot be null");
        // Force the id to derive from the name; beforeSave mints it from the slug.
        entity.setId(null);
        return super.create(entity)
                    .recover(ex -> ex instanceof AlreadyExistsException
                            ? Future.failedFuture(new AlreadyExistsException(
                                    "An organization named '" + entity.getName() + "' already exists"))
                            : Future.failedFuture(ex))
                    .compose(this::contained);
    }

    @Override
    public Future<Organization> createSync(Organization entity) {
        Validate.notBlank(entity.getName(), "Organization name cannot be null");
        entity.setId(null);
        return super.createSync(entity).compose(this::contained);
    }

    // The record before the graph, so a sign-up rolled back while the engine fails still frees the organization's
    // name; whatever a removal that fails leaves is removed when an organization is created under the same id
    @Override
    public Future<Void> deleteById(String id) {
        return super.deleteById(id).compose(v -> released(id));
    }

    @Override
    public Future<Void> deleteByIdSync(String id) {
        return super.deleteByIdSync(id).compose(v -> released(id));
    }

    // The organization leaves the graph with every grant made on it and every member it held
    private Future<Void> released(String id) {
        return relationships.removeObject(AuthzStoreService.SYSTEM, AuthzUtil.object(AuthzConstants.ORGANIZATION_TYPE, id));
    }

    // The organization's place in the graph, written once the record is, so a write that fails leaves an
    // organization nobody can reach rather than one nobody stores. What a deleted organization of the same id left
    // behind is removed first, so the organization starts with no member and no grant
    private Future<Organization> contained(Organization organization) {
        return released(organization.getId())
                .compose(v -> relationships.ensure(AuthzStoreService.SYSTEM,
                                                   List.of(AuthzGraphUtil.containment(AuthzConstants.SYSTEM_OBJECT,
                                                                                      AuthzUtil.object(AuthzConstants.ORGANIZATION_TYPE, organization.getId())))))
                .map(organization);
    }

    @Override
    public Future<List<OrganizationLoginLink>> sendOrganizationsEmail(String email) {
        Validate.notBlank(email, "email cannot be blank");
        return identityRepository.findOrganizationUsersByEmail(email)
                                 .compose(users -> findAllById(users.stream()
                                                                    .filter(ParticipantIdentity::isEnabled)
                                                                    .map(OrganizationParticipantIdentity::getOrganizationId)
                                                                    .toList()))
                                 .compose(organizations -> emailService.sendOrganizationsEmail(email, organizations));
    }
}
