package org.kinotic.os.internal.api.services.iam;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.os.api.model.iam.SystemOidcConfiguration;
import org.kinotic.os.api.services.iam.SystemOidcConfigurationService;
import org.kinotic.os.internal.api.services.AbstractCrudService;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultSystemOidcConfigurationService
        extends AbstractCrudService<SystemOidcConfiguration>
        implements SystemOidcConfigurationService {

    public DefaultSystemOidcConfigurationService(CrudServiceTemplate crudServiceTemplate,
                                                 ElasticsearchAsyncClient esAsyncClient,
                                                 SecurityContext securityContext) {
        super("kinotic_system_oidc_configuration",
              SystemOidcConfiguration.class,
              esAsyncClient, crudServiceTemplate, securityContext);
    }

    @Override
    public CompletableFuture<SystemOidcConfiguration> save(SystemOidcConfiguration entity) {
        Validate.notNull(entity.getName(), "SystemOidcConfiguration name cannot be null");
        Validate.notNull(entity.getProvider(), "SystemOidcConfiguration provider cannot be null");
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
            entity.setCreated(new Date());
        }
        entity.setUpdated(new Date());
        return super.save(entity);
    }

    @Override
    public CompletableFuture<List<SystemOidcConfiguration>> findAllEnabled() {
        return crudServiceTemplate.search(indexName, Pageable.create(0, 100, Sort.unsorted()), type, builder -> builder
                .query(q -> q.term(t -> t.field("enabled").value(true))))
                .thenApply(page -> page.getContent());
    }
}