package org.kinotic.system.internal.api.services.reconcile;

import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.kinotic.domain.api.repositories.ReconcilableRepository;
import org.kinotic.domain.api.services.Reconciler;
import org.kinotic.domain.api.repositories.WatchedRepository;
import org.kinotic.domain.api.model.WatchedType;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The workers and repositories the {@link ReconcileMaster} runs over, one of each per kind of record,
 * and the deployment of the master itself: every server node holds the registry, and Ignite elects
 * one of them to host the master.
 */
@Slf4j
@Component
public class ReconcilerRegistry {

    static final String MASTER_SINGLETON_NAME = "reconcile-master";

    private final Ignite ignite;
    private final Map<WatchedType, Reconciler<?>> workers = new EnumMap<>(WatchedType.class);
    private final Map<WatchedType, WatchedRepository<?>> repositories = new EnumMap<>(WatchedType.class);

    public ReconcilerRegistry(Ignite ignite, List<Reconciler<?>> reconcilers, List<WatchedRepository<?>> watchedRepositories) {
        this.ignite = ignite;
        for (Reconciler<?> reconciler : reconcilers) {
            Reconciler<?> other = workers.put(reconciler.type(), reconciler);
            if (other != null) {
                throw new IllegalStateException("Two reconcilers for " + reconciler.type() + ": " + other + " and " + reconciler);
            }
        }
        for (WatchedRepository<?> repository : watchedRepositories) {
            WatchedRepository<?> other = repositories.put(repository.type(), repository);
            if (other != null) {
                throw new IllegalStateException("Two repositories for " + repository.type() + ": " + other + " and " + repository);
            }
        }
    }

    // Every node requests the deployment; Ignite elects a single host for it cluster-wide
    @EventListener(ApplicationReadyEvent.class)
    public void deployMaster() {
        ignite.services().deployClusterSingleton(MASTER_SINGLETON_NAME, new ReconcileMaster());
    }

    /**
     * @param type a kind of record
     * @return its worker, empty for a kind that is watched and not reconciled
     */
    public Optional<Reconciler<?>> workerFor(WatchedType type) {
        return Optional.ofNullable(workers.get(type));
    }

    /**
     * @param type a kind of record
     * @return its repository, empty for a kind no repository was registered for
     */
    public Optional<WatchedRepository<?>> repositoryFor(WatchedType type) {
        return Optional.ofNullable(repositories.get(type));
    }

    /**
     * @return every registered repository
     */
    public Collection<WatchedRepository<?>> repositories() {
        return repositories.values();
    }

    /**
     * @return every registered repository whose records are reconciled
     */
    public List<ReconcilableRepository<?>> reconcilableRepositories() {
        List<ReconcilableRepository<?>> ret = new ArrayList<>();
        for (WatchedRepository<?> repository : repositories.values()) {
            if (repository instanceof ReconcilableRepository<?> reconcilable) {
                ret.add(reconcilable);
            }
        }
        return ret;
    }
}
