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
        Date now = new Date();
        entity.setId(slg.slugify(entity.getName()).toLowerCase())
              .setCreated(now)
              .setUpdated(now);
        return repository.create(entity)
                         .exceptionallyCompose(ex -> isAlreadyExists(ex)
                                 ? CompletableFuture.failedFuture(new AlreadyExistsException(
                                         "An organization named '" + entity.getName() + "' already exists"))
                                 : CompletableFuture.failedFuture(ex));
    }

    private static boolean isAlreadyExists(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof AlreadyExistsException) {
                return true;
            }
        }
        return false;
    }
}
