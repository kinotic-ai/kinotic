package org.kinotic.os.internal.api.services.iam;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.os.api.model.iam.OidcProviderKind;
import org.kinotic.os.api.model.iam.OrgSignupOidcConfiguration;
import org.kinotic.os.api.services.iam.OrgSignupOidcConfigurationService;
import org.kinotic.os.internal.api.services.AbstractCrudService;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultOrgSignupOidcConfigurationService
        extends AbstractCrudService<OrgSignupOidcConfiguration>
        implements OrgSignupOidcConfigurationService {

    public DefaultOrgSignupOidcConfigurationService(CrudServiceTemplate crudServiceTemplate,
                                                    ElasticsearchAsyncClient esAsyncClient,
                                                    SecurityContext securityContext) {
        super("kinotic_org_signup_oidc_configuration",
              OrgSignupOidcConfiguration.class,
              esAsyncClient, crudServiceTemplate, securityContext);
    }

    @Override
    public CompletableFuture<OrgSignupOidcConfiguration> save(OrgSignupOidcConfiguration entity) {
        Validate.notNull(entity.getName(), "OrgSignupOidcConfiguration name cannot be null");
        Validate.notNull(entity.getProvider(), "OrgSignupOidcConfiguration provider cannot be null");
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
            entity.setCreated(new Date());
        }
        entity.setUpdated(new Date());
        return super.save(entity);
    }

    @Override
    public CompletableFuture<List<OrgSignupOidcConfiguration>> findAllEnabled() {
        return crudServiceTemplate.search(indexName, Pageable.create(0, 100, Sort.unsorted()), type, builder -> builder
                .query(q -> q.term(t -> t.field("enabled").value(true))))
                .thenApply(page -> page.getContent());
    }

    @Override
    public CompletableFuture<OrgSignupOidcConfiguration> findEnabledByProvider(OidcProviderKind provider) {
        Validate.notNull(provider, "provider cannot be null");
        return crudServiceTemplate.search(indexName, Pageable.create(0, 1, Sort.unsorted()), type, builder -> builder
                .query(q -> q.bool(BoolQuery.of(b -> {
                    b.filter(TermQuery.of(t -> t.field("provider").value(provider.key()))._toQuery());
                    b.filter(TermQuery.of(t -> t.field("enabled").value(true))._toQuery());
                    return b;
                }))))
                .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }
}