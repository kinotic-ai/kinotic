package org.kinotic.authz.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.directory.ServiceDirectoryPublishedEvent;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the platform store's model equal to the one generated from every contract the directory holds. The
 * model is regenerated whenever this node stores entries in the directory, at startup and on a late
 * registration, so the store runs the union of what every node publishes once they have all started. A write
 * the engine refuses, because it is unreachable or holds no platform store, is retried until it succeeds;
 * the node serves meanwhile, since the store keeps the version it had.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformModelSynchronizer {

    static final long RETRY_DELAY_MS = 30_000;
    private static final int PAGE_SIZE = 500;

    private final AuthzModelGenerator generator;
    private final AuthzStoreService storeService;
    private final Vertx vertx;

    @EventListener
    public void onDirectoryPublished(ServiceDirectoryPublishedEvent event) {
        synchronize(event.directory());
    }

    private void synchronize(ServiceDirectory directory) {
        definitions(directory, 0, new ArrayList<>())
                .compose(definitions -> {
                    Future<String> ret;
                    try {
                        AuthzModel model = generator.platformModel(definitions);
                        ret = storeService.ensurePlatformModel(model)
                                          .onSuccess(id -> log.info("The platform store runs authorization model {}, generated from {} contracts",
                                                                    id, definitions.size()));
                    } catch (IllegalArgumentException e) {
                        // the contracts contradict each other, which a deploy fixes and a retry cannot
                        log.error("The platform authorization model cannot be generated from the published contracts", e);
                        ret = Future.succeededFuture();
                    }
                    return ret;
                })
                .onFailure(e -> {
                    log.warn("The platform authorization model could not be written, retrying in {} ms: {}",
                             RETRY_DELAY_MS, e.getMessage());
                    vertx.setTimer(RETRY_DELAY_MS, timer -> synchronize(directory));
                });
    }

    /**
     * Every contract in the directory, page by page, since the directory lists in pages only.
     */
    private Future<List<ServiceDefinition>> definitions(ServiceDirectory directory, int pageNumber, List<ServiceDefinition> collected) {
        return directory.findEntriesScopedTo(null, null, Pageable.create(pageNumber, PAGE_SIZE, Sort.by("id")))
                        .compose(page -> {
                            for (ServiceDirectoryEntry entry : page.getContent()) {
                                collected.add(entry.getServiceDefinition());
                            }
                            return page.getContent().size() < PAGE_SIZE
                                    ? Future.succeededFuture(collected)
                                    : definitions(directory, pageNumber + 1, collected);
                        });
    }

}
