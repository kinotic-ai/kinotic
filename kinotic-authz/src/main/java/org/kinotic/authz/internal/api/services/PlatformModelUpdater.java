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
import org.kinotic.core.api.directory.ServiceDirectoryChange;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.event.EventFabricService;
import org.kinotic.idl.api.schema.ServiceDefinition;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the platform store's model equal to the one generated from every platform contract the directory holds,
 * running as one HA cluster singleton on the Ignite service grid: it syncs when elected, after a quiet window
 * following any node's directory writes, and on a period, so the store runs the union of what every node
 * publishes and a write the engine refused is made again.
 */
@Slf4j
public class PlatformModelUpdater implements Service {

    static final String NAME = "kinotic-platform-model-updater";
    private static final long SETTLE_WINDOW_MS = 15_000;
    private static final long RECONCILE_INTERVAL_MS = 600_000;
    private static final int PAGE_SIZE = 500;

    // Injected by Ignite on the node elected to host the singleton
    @SpringResource(resourceClass = ServiceDirectory.class)
    private transient ServiceDirectory directory;
    @SpringResource(resourceClass = AuthzModelGenerator.class)
    private transient AuthzModelGenerator generator;
    @SpringResource(resourceClass = AuthzStoreService.class)
    private transient AuthzStoreService storeService;
    @SpringResource(resourceClass = EventFabricService.class)
    private transient EventFabricService fabric;
    @SpringResource(resourceClass = Vertx.class)
    private transient Vertx vertx;

    private transient Disposable changes;
    private transient long settleTimerId;
    private transient long reconcileTimerId;

    @Override
    public void init() {
        log.info("Starting platform model updater singleton");
        // this instance is not a bean the fabric wires on its own; the join makes the subscription
        // deterministic before the first sync, as the fabric does for beans
        changes = fabric.registerConsumer(ServiceDirectoryChange.class, this::onEntryChange)
                        .toCompletionStage()
                        .toCompletableFuture()
                        .join();
        // the election is a reason to sync: the previous host may have died mid-write, and a change delivered
        // before this instance subscribed went unheard
        settle();
        reconcileTimerId = vertx.setPeriodic(RECONCILE_INTERVAL_MS, id -> synchronize());
    }

    @Override
    public void execute() {
        // passive service: all work is driven by the fabric and the timers started in init
    }

    @Override
    public void cancel() {
        log.info("Stopping platform model updater singleton");
        changes.dispose();
        vertx.cancelTimer(settleTimerId);
        vertx.cancelTimer(reconcileTimerId);
    }

    // an application's contract belongs to its own model
    private void onEntryChange(ServiceDirectoryChange change) {
        if (change.getOrganizationId() == null) {
            settle();
        }
    }

    // One sync per window: a node publishes its services as a burst, and the sync reads the directory at fire
    // time, so every change before it is covered
    private synchronized void settle() {
        if (settleTimerId == 0) {
            settleTimerId = vertx.setTimer(SETTLE_WINDOW_MS, id -> {
                synchronized (this) {
                    settleTimerId = 0;
                }
                synchronize();
            });
        }
    }

    private void synchronize() {
        definitions(0, new ArrayList<>())
                .compose(definitions -> storeService.ensurePlatformModel(generator.platformModel(definitions))
                                                    .onSuccess(id -> log.info("The platform store runs authorization model {}, generated from {} contracts",
                                                                              id, definitions.size())))
                .onFailure(e -> log.warn("The platform authorization model could not be written: {}", e.getMessage()));
    }

    /**
     * Every contract in the directory, page by page, since the directory lists in pages only.
     */
    private Future<List<ServiceDefinition>> definitions(int pageNumber, List<ServiceDefinition> collected) {
        return directory.findEntriesScopedTo(null, null, Pageable.create(pageNumber, PAGE_SIZE, Sort.by("id")))
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
