package org.kinotic.os.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import com.github.slugify.Slugify;
import org.apache.commons.lang3.Validate;
import org.kinotic.os.api.model.Organization;
import org.kinotic.os.api.services.OrganizationService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultOrganizationService extends AbstractCrudService<Organization> implements OrganizationService {

    private final Slugify slg = Slugify.builder().underscoreSeparator(true).build();

    public DefaultOrganizationService(CrudServiceTemplate crudServiceTemplate,
                                      ElasticsearchAsyncClient esAsyncClient) {
        super("kinotic_organization", Organization.class, esAsyncClient, crudServiceTemplate);
    }

    @Override
    public CompletableFuture<Organization> save(Organization entity) {
        Validate.notNull(entity.getName(), "Organization name cannot be null");

        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
            entity.setCreated(new Date());
        }

        entity.setSlug(slg.slugify(entity.getName()).toLowerCase());
        entity.setUpdated(new Date());
        return super.save(entity);
    }

}
