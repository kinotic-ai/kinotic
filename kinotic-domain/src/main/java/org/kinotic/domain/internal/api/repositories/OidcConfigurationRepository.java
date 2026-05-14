package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.mget.MultiGetOperation;
import org.kinotic.domain.api.model.iam.OidcConfiguration;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Repository
public class OidcConfigurationRepository extends AbstractRepository<OidcConfiguration> {

    public OidcConfigurationRepository(ElasticsearchAsyncClient esAsyncClient,
                                       CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_oidc_configuration", OidcConfiguration.class, esAsyncClient, crudServiceTemplate);
    }

    /**
     * Multi-get the given ids and return only those whose source is non-null and {@code enabled}.
     */
    public CompletableFuture<List<OidcConfiguration>> findEnabledByIds(List<String> ids) {
        List<MultiGetOperation> ops = ids.stream()
                                         .map(id -> MultiGetOperation.of(o -> o.index(indexName).id(id)))
                                         .toList();
        return esAsyncClient.mget(MgetRequest.of(r -> r.docs(ops)), OidcConfiguration.class)
                            .thenApply(response -> response.docs().stream()
                                                           .filter(doc -> doc.result().found() && doc.result().source() != null)
                                                           .map(doc -> doc.result().source())
                                                           .filter(Objects::nonNull)
                                                           .filter(OidcConfiguration::isEnabled)
                                                           .toList());
    }
}
