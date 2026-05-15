package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.kinotic.domain.api.model.iam.OidcConfiguration;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
public class OidcConfigurationRepository extends AbstractOrganizationScopedRepository<OidcConfiguration> {

    public OidcConfigurationRepository(ElasticsearchAsyncClient esAsyncClient,
                                       CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_oidc_configuration", OidcConfiguration.class, esAsyncClient, crudServiceTemplate);
    }

    /**
     * Multi-get the given ids and return only those that are {@code enabled}.
     */
    public CompletableFuture<List<OidcConfiguration>> findEnabledByIds(List<String> ids) {
        return multiGetByIds(ids, OidcConfiguration::isEnabled);
    }
}
