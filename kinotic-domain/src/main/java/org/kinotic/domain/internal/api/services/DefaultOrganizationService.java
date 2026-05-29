package org.kinotic.domain.internal.api.services;

import com.github.slugify.Slugify;
import org.apache.commons.lang3.Validate;
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
    public CompletableFuture<Organization> save(Organization entity) {
        Validate.notNull(entity.getName(), "Organization name cannot be null");

        if (entity.getId() == null) {
            entity.setId(slg.slugify(entity.getName()).toLowerCase());
            entity.setCreated(new Date());
        }

        entity.setUpdated(new Date());
        return super.save(entity);
    }
}
