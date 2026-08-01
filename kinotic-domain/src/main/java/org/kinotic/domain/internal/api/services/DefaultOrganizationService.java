package org.kinotic.domain.internal.api.services;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.internal.api.repositories.OrganizationRepository;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultOrganizationService extends AbstractCrudService<Organization> implements OrganizationService {

    /**
     * No organization can be saved with an id that begins with this prefix. It is used
     * internally when the platform needs multi-tenancy but the system is the tenant —
     * e.g. VM workloads executed by the OS for the OS.
     */
    public static final String RESERVED_ID_PREFIX = "kinotic";

    public DefaultOrganizationService(OrganizationRepository repository) {
        super(repository);
    }

    @Override
    protected CompletableFuture<Void> beforeSave(Organization entity) {
        Validate.notNull(entity.getName(), "Organization name cannot be null");

        if (entity.getId() == null) {
            entity.setId(DomainUtil.slugifyId(entity.getName()));
            entity.setCreated(new Date());
        }
        // Validate only; re-minting an update's id would silently write a new document
        DomainUtil.validateOrganizationId(entity.getId());
        // Reserved-id organizations are only ever seeded by db migrations, which bypass this service
        Validate.isTrue(!entity.getId().startsWith(RESERVED_ID_PREFIX),
                        "Organization id '%s' is reserved", entity.getId());

        entity.setUpdated(new Date());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Creates a new organization, deriving its id from the slugified name. Fails with
     * {@link AlreadyExistsException} if an organization with that name (hence id) already
     * exists, rather than overwriting it.
     */
    @Override
    public CompletableFuture<Organization> create(Organization entity) {
        Validate.notBlank(entity.getName(), "Organization name cannot be null");
        // Force the id to derive from the name; beforeSave mints it from the slug.
        entity.setId(null);
        return super.create(entity)
                    .exceptionallyCompose(ex -> AlreadyExistsException.isCause(ex)
                            ? CompletableFuture.failedFuture(new AlreadyExistsException(
                                    "An organization named '" + entity.getName() + "' already exists"))
                            : CompletableFuture.failedFuture(ex));
    }
}
