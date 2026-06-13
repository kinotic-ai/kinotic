package org.kinotic.domain.internal.api.services;

import com.github.slugify.Slugify;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.internal.api.repositories.OrganizationRepository;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultOrganizationService extends AbstractCrudService<Organization> implements OrganizationService {

    private final Slugify slg = Slugify.builder().underscoreSeparator(true).build();

    public DefaultOrganizationService(OrganizationRepository repository) {
        super(repository);
    }

    @Override
    protected CompletableFuture<Void> beforeSave(Organization entity) {
        Validate.notNull(entity.getName(), "Organization name cannot be null");

        if (entity.getId() == null) {
            entity.setId(slg.slugify(entity.getName()).toLowerCase());
            entity.setCreated(new Date());
        }

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
