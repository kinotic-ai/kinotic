package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.iam.SystemOidcConfiguration;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class SystemOidcConfigurationRepository extends AbstractRepository<SystemOidcConfiguration> {

    public SystemOidcConfigurationRepository(ElasticsearchAsyncClient esAsyncClient,
                                             CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_system_oidc_configuration", SystemOidcConfiguration.class, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<List<SystemOidcConfiguration>> findAllEnabled() {
        return findAll(Pageable.ofSize(100), b -> b.query(termFilter("enabled", true)))
                .thenApply(Page::getContent);
    }
}
