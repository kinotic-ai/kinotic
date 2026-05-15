package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.api.model.iam.OidcProviderKind;
import org.kinotic.domain.api.model.iam.OrgSignupOidcConfiguration;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
public class OrgSignupOidcConfigurationRepository extends AbstractRepository<OrgSignupOidcConfiguration> {

    public OrgSignupOidcConfigurationRepository(ElasticsearchAsyncClient esAsyncClient,
                                                CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_org_signup_oidc_configuration", OrgSignupOidcConfiguration.class, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<List<OrgSignupOidcConfiguration>> findAllEnabled() {
        return findAll(Pageable.create(0, 100, Sort.unsorted()),
                        b -> b.query(q -> q.term(t -> t.field("enabled").value(true))))
                .thenApply(Page::getContent);
    }

    public CompletableFuture<OrgSignupOidcConfiguration> findEnabledByProvider(OidcProviderKind provider) {
        return findAll(Pageable.create(0, 1, Sort.unsorted()), b -> b
                .query(q -> q.bool(BoolQuery.of(bb -> {
                    bb.filter(TermQuery.of(t -> t.field("provider").value(provider.key()))._toQuery());
                    bb.filter(TermQuery.of(t -> t.field("enabled").value(true))._toQuery());
                    return bb;
                }))))
                .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }
}
