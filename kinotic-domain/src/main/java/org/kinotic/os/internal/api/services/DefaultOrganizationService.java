package org.kinotic.os.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import com.github.slugify.Slugify;
import org.apache.commons.lang3.Validate;
import org.kinotic.os.api.model.Organization;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.kinotic.os.api.services.OrganizationService;
import org.kinotic.os.api.services.iam.OidcConfigurationService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultOrganizationService extends AbstractCrudService<Organization> implements OrganizationService {

    private final Slugify slg = Slugify.builder().underscoreSeparator(true).build();
    private final OidcConfigurationService oidcConfigurationService;

    public DefaultOrganizationService(CrudServiceTemplate crudServiceTemplate,
                                      ElasticsearchAsyncClient esAsyncClient,
                                      OidcConfigurationService oidcConfigurationService) {
        super("kinotic_organization", Organization.class, esAsyncClient, crudServiceTemplate);
        this.oidcConfigurationService = oidcConfigurationService;
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

    @Override
    public CompletableFuture<List<OidcConfiguration>> getOidcConfigurations(String organizationId) {
        Validate.notNull(organizationId, "organizationId cannot be null");
        return findById(organizationId)
                .thenCompose(organization -> {
                    Validate.notNull(organization, "Organization not found: %s", organizationId);
                    return oidcConfigurationService.findEnabledByIds(organization.getOidcConfigurationIds());
                });
    }

}
