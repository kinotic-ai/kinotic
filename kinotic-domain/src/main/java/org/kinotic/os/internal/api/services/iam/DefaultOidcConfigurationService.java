package org.kinotic.os.internal.api.services.iam;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.mget.MultiGetOperation;
import org.apache.commons.lang3.Validate;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.kinotic.os.api.services.iam.OidcConfigurationService;
import org.kinotic.os.internal.api.services.AbstractCrudService;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultOidcConfigurationService extends AbstractCrudService<OidcConfiguration> implements OidcConfigurationService {

    public DefaultOidcConfigurationService(CrudServiceTemplate crudServiceTemplate,
                                           ElasticsearchAsyncClient esAsyncClient) {
        super("kinotic_oidc_configuration", OidcConfiguration.class, esAsyncClient, crudServiceTemplate);
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
                                    .map(doc -> doc.result().source())
                                    .filter(OidcConfiguration::isEnabled)
                                    .toList());
    }

}

