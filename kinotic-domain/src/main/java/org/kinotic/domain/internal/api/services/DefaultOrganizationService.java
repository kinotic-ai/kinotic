package org.kinotic.domain.internal.api.services;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.services.OrganizationProvisioner;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.internal.api.repositories.OrganizationRepository;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
public class DefaultOrganizationService extends AbstractCrudService<Organization> implements OrganizationService {

    private final ObjectProvider<OrganizationProvisioner> provisioners;

    public DefaultOrganizationService(OrganizationRepository repository, ObjectProvider<OrganizationProvisioner> provisioners) {
        super(repository);
        this.provisioners = provisioners;
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
     * Creates a new organization, deriving its id from the slugified name, and starts every
     * {@link OrganizationProvisioner} on it. Fails with {@link AlreadyExistsException} if an
     * organization with that name (hence id) already exists, rather than overwriting it.
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
                    .compose(created -> provision(created).map(created));
    }

    // Each provisioner records its own outcome on the organization, so one that fails to
    // start leaves the organization created, and is logged
    private Future<Void> provision(Organization organization) {
        Future<Void> ret = Future.succeededFuture();
        for (OrganizationProvisioner provisioner : provisioners.orderedStream().toList()) {
            ret = ret.compose(v -> provisioner.provision(organization)
                    .recover(error -> {
                        log.error("{} could not start on organization {}", provisioner.getClass().getSimpleName(),
                                  organization.getId(), error);
                        return Future.succeededFuture();
                    }));
        }
        return ret;
    }
}
