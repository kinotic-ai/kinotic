package org.kinotic.domain.internal.api.services;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.OrganizationLoginLink;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
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
import java.util.function.Function;

@Slf4j
@Component
public class DefaultOrganizationService extends AbstractCrudService<Organization> implements OrganizationService {

    private static final int LEFT_USERS_PAGE_SIZE = 100;

    private final ParticipantIdentityRepository identityRepository;
    private final ParticipantIdentityService identityService;
    private final EmailService emailService;
    private final RelationshipService relationships;

    public DefaultOrganizationService(OrganizationRepository repository,
                                      ParticipantIdentityRepository identityRepository,
                                      ParticipantIdentityService identityService,
                                      EmailService emailService,
                                      RelationshipService relationships) {
        super(repository);
        this.identityRepository = identityRepository;
        this.identityService = identityService;
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
        return createExclusively(entity, super::create);
    }

    @Override
    public Future<Organization> createSync(Organization entity) {
        return createExclusively(entity, super::createSync);
    }

    // One caller at a time creates or deletes the organization of an id. Whatever a deleted organization of the id
    // left behind is removed before the record is written, so the new organization is never seen with a member or a
    // grant of the old one, and a deletion's removal never takes the place a creation just wrote
    private Future<Organization> createExclusively(Organization entity, Function<Organization, Future<Organization>> createOp) {
        Validate.notBlank(entity.getName(), "Organization name cannot be null");
        // Force the id to derive from the name; beforeSave mints it from the slug.
        entity.setId(null);
        String id = DomainUtil.slugifyId(entity.getName());
        DomainUtil.validateOrganizationId(id);
        return relationships.exclusively(objectOf(id), () -> repository.findById(id)
                .compose(existing -> existing == null
                        ? cleared(id)
                        : Future.failedFuture(new AlreadyExistsException(id)))
                .compose(v -> createOp.apply(entity))
                .recover(ex -> AlreadyExistsException.isCause(ex)
                        ? Future.failedFuture(new AlreadyExistsException("An organization named '" + entity.getName() + "' already exists"))
                        : Future.failedFuture(ex))
                .compose(this::contained));
    }

    // The record before the graph, so a sign-up rolled back while the engine fails still frees the organization's
    // name; whatever a removal that fails leaves is removed before an organization is created under the same id
    @Override
    public Future<Void> deleteById(String id) {
        return relationships.exclusively(objectOf(id), () -> super.deleteById(id).compose(v -> released(id)));
    }

    @Override
    public Future<Void> deleteByIdSync(String id) {
        return relationships.exclusively(objectOf(id), () -> super.deleteByIdSync(id).compose(v -> released(id)));
    }

    private static String objectOf(String id) {
        return AuthzUtil.object(AuthzConstants.ORGANIZATION_TYPE, id);
    }

    // The organization leaves the graph with every grant made on it and every member it held
    private Future<Void> released(String id) {
        return relationships.removeObject(AuthzStoreService.SYSTEM, objectOf(id));
    }

    // What a deleted organization of the id left behind: its place in the graph, when the removal after its record's
    // deletion failed, and its users. A sign-up rolled back while the engine fails leaves its founder, whose
    // deletion asks the engine first, and who could otherwise sign in to the organization created under the id
    private Future<Void> cleared(String id) {
        return released(id).compose(v -> usersRemoved(id));
    }

    // Each deletion is searchable before the next page is read, so the first page is read until it holds no one
    private Future<Void> usersRemoved(String organizationId) {
        return identityRepository.findOrganizationUsers(organizationId, Pageable.ofSize(LEFT_USERS_PAGE_SIZE))
                                 .compose(left -> {
                                     Future<Void> ret = Future.succeededFuture();
                                     for (OrganizationParticipantIdentity user : left.getContent()) {
                                         ret = ret.compose(v -> identityService.deleteByIdSync(user.getId()));
                                     }
                                     return left.getContent().isEmpty() ? ret : ret.compose(v -> usersRemoved(organizationId));
                                 });
    }

    // The organization's place in the graph, written once the record is. A record whose place could not be written
    // is removed again, so an engine failure does not leave the name held by an organization nobody can reach; the
    // record alone is removed, since the graph holds nothing of it yet
    private Future<Organization> contained(Organization organization) {
        return relationships.ensure(AuthzStoreService.SYSTEM,
                                    List.of(AuthzGraphUtil.containment(AuthzConstants.SYSTEM_OBJECT, objectOf(organization.getId()))))
                            .map(organization)
                            .recover(err -> super.deleteByIdSync(organization.getId())
                                                 .onFailure(deleteErr -> log.warn("Could not remove organization {} after its graph placement failed: {}",
                                                                                  organization.getId(), deleteErr.getMessage()))
                                                 .transform(v -> Future.failedFuture(err)));
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
