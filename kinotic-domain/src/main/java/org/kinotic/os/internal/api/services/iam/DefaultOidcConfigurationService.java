package org.kinotic.os.internal.api.services.iam;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.mget.MultiGetOperation;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.os.api.model.Organization;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.kinotic.os.api.services.OrganizationService;
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

    private final OrganizationService organizationService;

    public DefaultOidcConfigurationService(CrudServiceTemplate crudServiceTemplate,
                                           ElasticsearchAsyncClient esAsyncClient,
                                           OrganizationService organizationService,
                                           SecurityContext securityContext) {
        super("kinotic_oidc_configuration", OidcConfiguration.class, esAsyncClient, crudServiceTemplate, securityContext);
        this.organizationService = organizationService;
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
    public CompletableFuture<OidcConfiguration> findOrgLoginConfig(String organizationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return organizationService.findById(organizationId).thenCompose(org -> {
            if (org == null || org.getSsoConfigId() == null) {
                return CompletableFuture.completedFuture(null);
            }
            // Direct doc lookup bypasses AbstractCrudService scope enforcement so the
            // pre-auth login-lookup path can resolve without a participant. Defense in
            // depth: also confirm the row's organizationId matches and it's enabled.
            return crudServiceTemplate.findById(indexName, org.getSsoConfigId(), type, b -> b.routing(organizationId))
                                      .thenApply(c -> validForOrgLogin(c, organizationId));
        });
    }

    private static OidcConfiguration validForOrgLogin(OidcConfiguration config, String expectedOrgId) {
        if (config == null) return null;
        if (!config.isEnabled()) return null;
        if (!Objects.equals(expectedOrgId, config.getOrganizationId())) return null;
        return config;
    }
}
