package org.kinotic.os.internal.services.iam;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.mget.MultiGetOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.api.model.Application;
import org.kinotic.os.api.model.KinoticSystem;
import org.kinotic.os.api.model.Organization;
import org.kinotic.os.api.model.iam.AuthScope;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class OidcConfigLookup {

    private static final String OIDC_CONFIG_INDEX = "kinotic_oidc_configuration";
    private static final String SYSTEM_INDEX = "kinotic_system";
    private static final String ORGANIZATION_INDEX = "kinotic_organization";
    private static final String APPLICATION_INDEX = "kinotic_application";

    private final ElasticsearchAsyncClient esAsyncClient;

    public CompletableFuture<List<OidcConfiguration>> getConfigsForScope(String authScopeType, String authScopeId) {
        AuthScope scope;
        try {
            scope = AuthScope.valueOf(authScopeType);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid authScopeType: " + authScopeType));
        }

        return switch (scope) {
            case SYSTEM -> getConfigIdsFromSystem()
                    .thenCompose(this::fetchOidcConfigurations);
            case ORGANIZATION -> getConfigIdsFromEntity(ORGANIZATION_INDEX, authScopeId, Organization.class)
                    .thenApply(org -> org != null ? org.getOidcConfigurationIds() : null)
                    .thenCompose(this::fetchOidcConfigurations);
            case APPLICATION -> getConfigIdsFromEntity(APPLICATION_INDEX, authScopeId, Application.class)
                    .thenApply(app -> app != null ? app.getOidcConfigurationIds() : null)
                    .thenCompose(this::fetchOidcConfigurations);
        };
    }

    private CompletableFuture<List<String>> getConfigIdsFromSystem() {
        return esAsyncClient.get(g -> g.index(SYSTEM_INDEX).id("kinotic-system"), KinoticSystem.class)
                            .thenApply(response -> {
                                if (response.found() && response.source() != null) {
                                    return response.source().getOidcConfigurationIds();
                                }
                                return null;
                            });
    }

    private <T> CompletableFuture<T> getConfigIdsFromEntity(String index, String id, Class<T> type) {
        if (id == null) {
            return CompletableFuture.completedFuture(null);
        }
        return esAsyncClient.get(g -> g.index(index).id(id), type)
                            .thenApply(response -> {
                                if (response.found()) {
                                    return response.source();
                                }
                                return null;
                            });
    }

    private CompletableFuture<List<OidcConfiguration>> fetchOidcConfigurations(List<String> configIds) {
        if (configIds == null || configIds.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        List<MultiGetOperation> ops = configIds.stream()
                .map(id -> MultiGetOperation.of(o -> o.index(OIDC_CONFIG_INDEX).id(id)))
                .toList();

        return esAsyncClient.mget(MgetRequest.of(r -> r.docs(ops)), OidcConfiguration.class)
                            .thenApply(response -> response.docs().stream()
                                    .filter(doc -> doc.result().found() && doc.result().source() != null)
                                    .map(doc -> doc.result().source())
                                    .filter(OidcConfiguration::isEnabled)
                                    .toList());
    }

}
