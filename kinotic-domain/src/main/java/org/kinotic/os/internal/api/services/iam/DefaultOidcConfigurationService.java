package org.kinotic.os.internal.api.services.iam;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.mget.MultiGetOperation;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.kinotic.os.api.services.iam.OidcConfigurationService;
import org.kinotic.os.internal.api.services.AbstractCrudService;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultOidcConfigurationService extends AbstractCrudService<OidcConfiguration> implements OidcConfigurationService {

    public DefaultOidcConfigurationService(CrudServiceTemplate crudServiceTemplate,
                                           ElasticsearchAsyncClient esAsyncClient,
                                           SecurityContext securityContext) {
        super("kinotic_oidc_configuration", OidcConfiguration.class, esAsyncClient, crudServiceTemplate, securityContext);
    }

    @Override
    public CompletableFuture<OidcConfiguration> save(OidcConfiguration entity) {
        Validate.notNull(entity.getName(), "OidcConfiguration name cannot be null");
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
            entity.setCreated(new Date());
        }
        entity.setUpdated(new Date());
        return super.save(entity);
    }

    @Override
    public CompletableFuture<List<OidcConfiguration>> findEnabledByIds(List<String> ids) {
        Validate.notEmpty(ids, "ids cannot be null or empty");

        List<MultiGetOperation> ops = ids.stream()
                .map(id -> MultiGetOperation.of(o -> o.index(indexName).id(id)))
                .toList();

        return esAsyncClient.mget(MgetRequest.of(r -> r.docs(ops)), OidcConfiguration.class)
                            .thenApply(response -> response.docs().stream()
                                                           .filter(doc -> doc.result().found() && doc.result().source() != null)
                                                           .map(doc -> doc.result().source()).filter(Objects::nonNull)
                                                           .filter(OidcConfiguration::isEnabled)
                                                           .toList());
    }

    @Override
    public CompletableFuture<List<OidcConfiguration>> findEnabledPlatformWide() {
        return crudServiceTemplate.search(indexName, Pageable.create(0, 100, Sort.unsorted()), type, builder -> builder
                .query(q -> q.bool(BoolQuery.of(b -> {
                    b.filter(TermQuery.of(t -> t.field("enabled").value(true))._toQuery());
                    b.filter(TermQuery.of(t -> t.field("platformWide").value(true))._toQuery());
                    return b;
                }))))
                .thenApply(page -> page.getContent());
    }

}
