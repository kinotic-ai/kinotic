package org.kinotic.domain.internal.api.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.OrganizationLoginLink;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.internal.api.repositories.OrganizationRepository;
import org.kinotic.domain.internal.api.repositories.ParticipantIdentityRepository;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DefaultOrganizationService extends AbstractCrudService<Organization> implements OrganizationService {

    private final ParticipantIdentityRepository identityRepository;
    private final EmailService emailService;

    public DefaultOrganizationService(OrganizationRepository repository,
                                      ParticipantIdentityRepository identityRepository,
                                      EmailService emailService) {
        super(repository);
        this.identityRepository = identityRepository;
        this.emailService = emailService;
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
                            : Future.failedFuture(ex));
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

    @Override
    public Future<Boolean> deactivate(String organizationId) {
        return setDeactivated(organizationId, new Date());
    }

    @Override
    public Future<Boolean> reactivate(String organizationId) {
        return setDeactivated(organizationId, null);
    }

    // emits whether this call moved the organization between active and deactivated
    private Future<Boolean> setDeactivated(String organizationId, Date deactivated) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return repository.findById(organizationId).compose(found -> {
            Validate.isTrue(found != null, "No organization has the id %s", organizationId);
            Future<Boolean> ret;
            if (isDeactivated(found) == (deactivated != null)) {
                ret = Future.succeededFuture(false);
            } else {
                AtomicBoolean changed = new AtomicBoolean();
                // read and changed again by update, so a write landing since the read above is kept
                ret = repository.update(organizationId, organization -> {
                    changed.set(isDeactivated(organization) != (deactivated != null));
                    if (changed.get()) {
                        organization.setDeactivated(deactivated).setUpdated(new Date());
                    }
                    return Future.succeededFuture(organization);
                }).map(organization -> changed.get());
            }
            return ret;
        });
    }

    private static boolean isDeactivated(Organization organization) {
        return organization.getDeactivated() != null;
    }
}
