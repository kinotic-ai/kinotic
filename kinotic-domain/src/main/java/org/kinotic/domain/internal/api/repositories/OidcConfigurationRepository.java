package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.IdsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.iam.OidcConfiguration;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class OidcConfigurationRepository extends AbstractOrganizationScopedRepository<OidcConfiguration> {

    public OidcConfigurationRepository(ElasticsearchAsyncClient esAsyncClient,
                                       CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_oidc_configuration", OidcConfiguration.class, esAsyncClient, crudServiceTemplate);
    }

    /**
     * Returns the configurations among {@code ids} whose {@code enabled} flag is true.
     */
    public CompletableFuture<List<OidcConfiguration>> findEnabledByIds(List<String> ids) {
        Query query = composeFilter(IdsQuery.of(i -> i.values(ids))._toQuery(),
                                    TermQuery.of(t -> t.field("enabled").value(true))._toQuery());
        return findAll(Pageable.ofSize(ids.size()), b -> b.query(query))
                .thenApply(Page::getContent);
    }
}
