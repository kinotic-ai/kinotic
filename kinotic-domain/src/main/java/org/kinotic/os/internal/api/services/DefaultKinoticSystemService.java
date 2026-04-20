package org.kinotic.os.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.api.model.KinoticSystem;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.kinotic.os.api.services.KinoticSystemService;
import org.kinotic.os.api.services.iam.OidcConfigurationService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultKinoticSystemService implements KinoticSystemService {

    private static final String INDEX_NAME = "kinotic_system";
    private static final String SYSTEM_ID = "kinotic-system";

    private final ElasticsearchAsyncClient esAsyncClient;
    private final CrudServiceTemplate crudServiceTemplate;
    private final OidcConfigurationService oidcConfigurationService;

    @PostConstruct
    public void verifyIndexExists() {
        crudServiceTemplate.verifyIndexExists(INDEX_NAME);
    }

    @Override
    public CompletableFuture<KinoticSystem> getSystem() {
        return esAsyncClient.get(g -> g.index(INDEX_NAME).id(SYSTEM_ID), KinoticSystem.class)
                            .thenApply(response -> {
                                if (response.found()) {
                                    return response.source();
                                }
                                // Return a default system object if none exists
                                return new KinoticSystem().setId(SYSTEM_ID);
                            });
    }

    @Override
    public CompletableFuture<KinoticSystem> save(KinoticSystem system) {
        system.setId(SYSTEM_ID);
        system.setUpdated(new Date());
        return esAsyncClient.index(i -> i
                .index(INDEX_NAME)
                .id(SYSTEM_ID)
                .document(system)
                .refresh(Refresh.WaitFor))
                .thenApply(response -> system);
    }

    @Override
    public CompletableFuture<List<OidcConfiguration>> getOidcConfigurations() {
        return getSystem()
                .thenCompose(system -> {
                    List<String> ids = system.getOidcConfigurationIds();
                    if (ids == null || ids.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }
                    return oidcConfigurationService.findEnabledByIds(ids);
                });
    }

}
