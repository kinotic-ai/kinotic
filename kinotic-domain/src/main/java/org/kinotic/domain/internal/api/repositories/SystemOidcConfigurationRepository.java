package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
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
        return findAll(Pageable.create(0, 100, Sort.unsorted()),
                        b -> b.query(q -> q.term(t -> t.field("enabled").value(true))))
                .thenApply(Page::getContent);
    }
}
