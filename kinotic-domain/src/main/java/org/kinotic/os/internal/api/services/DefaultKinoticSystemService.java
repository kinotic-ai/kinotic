package org.kinotic.os.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.api.model.KinoticSystem;
import org.kinotic.os.api.services.KinoticSystemService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultKinoticSystemService implements KinoticSystemService {

    private static final String INDEX_NAME = "kinotic_system";
    private static final String SYSTEM_ID = "kinotic-system";

    private final ElasticsearchAsyncClient esAsyncClient;

    @PostConstruct
    public void verifyIndexExists() {
        try {
            boolean exists = esAsyncClient.indices()
                                          .exists(b -> b.index(INDEX_NAME))
                                          .get()
                                          .value();
            if (!exists) {
                throw new IllegalStateException(
                        "Elasticsearch index '" + INDEX_NAME + "' does not exist. "
                        + "Did you forget to add a migration in kinotic-migration/src/main/resources/migrations/?");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to verify existence of index '" + INDEX_NAME + "'", e);
        }
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

}
