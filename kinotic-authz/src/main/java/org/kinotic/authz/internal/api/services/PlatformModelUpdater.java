package org.kinotic.authz.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.resources.SpringResource;
import org.apache.ignite.services.Service;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.idl.api.schema.ServiceDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the platform store's model equal to the one generated from every platform contract the directory holds,
 * running as one HA cluster singleton on the Ignite service grid: it syncs when elected and every fifteen
 * seconds after, so the store runs the union of what every node publishes within that interval and a write the
 * engine refused is made again. No other node writes the model.
 */
@Slf4j
public class PlatformModelUpdater implements Service {

    static final String NAME = "kinotic-platform-model-updater";
    private static final long SYNC_INTERVAL_MS = 15_000;
    private static final int PAGE_SIZE = 500;

    // Injected by Ignite on the node elected to host the singleton
    @SpringResource(resourceClass = ServiceDirectory.class)
    private transient ServiceDirectory directory;
    @SpringResource(resourceClass = AuthzModelGenerator.class)
    private transient AuthzModelGenerator generator;
    @SpringResource(resourceClass = AuthzStoreService.class)
    private transient AuthzStoreService storeService;
    @SpringResource(resourceClass = Vertx.class)
    private transient Vertx vertx;

    private transient long timerId;
    private transient volatile boolean syncing;

    @Override
    public void init() {
        log.info("Starting platform model updater singleton");
        // the election is a reason to sync: the previous host may have died mid-write
        synchronize();
        timerId = vertx.setPeriodic(SYNC_INTERVAL_MS, id -> synchronize());
    }

    @Override
    public void execute() {
        // passive service: all work is driven by the timer started in init
    }

    @Override
    public void cancel() {
        log.info("Stopping platform model updater singleton");
        vertx.cancelTimer(timerId);
    }

    private void synchronize() {
        // a slow engine must not stack syncs; the tick after the one in flight completes picks up what it missed
        if (!syncing) {
            syncing = true;
            definitions(0, new ArrayList<>())
                    .compose(definitions -> storeService.ensurePlatformModel(generator.platformModel(definitions))
                                                        .onSuccess(id -> log.debug("The platform store runs authorization model {}, generated from {} contracts",
                                                                                   id, definitions.size())))
                    .onFailure(e -> log.warn("The platform authorization model could not be written: {}", e.getMessage()))
                    .onComplete(r -> syncing = false);
        }
    }

    /**
     * Every platform contract in the directory, page by page, since the directory lists in pages only.
     */
    private Future<List<ServiceDefinition>> definitions(int pageNumber, List<ServiceDefinition> collected) {
        return directory.findSystemEntries(Pageable.create(pageNumber, PAGE_SIZE, Sort.by("id")))
                        .compose(page -> {
                            for (ServiceDirectoryEntry entry : page.getContent()) {
                                collected.add(entry.getServiceDefinition());
                            }
                            return page.getContent().size() < PAGE_SIZE
                                    ? Future.succeededFuture(collected)
                                    : definitions(pageNumber + 1, collected);
                        });
    }

}
