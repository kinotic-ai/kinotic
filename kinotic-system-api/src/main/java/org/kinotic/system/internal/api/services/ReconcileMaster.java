package org.kinotic.system.internal.api.services;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.resources.SpringResource;
import org.apache.ignite.services.Service;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.reconcile.Reconcilable;
import org.kinotic.core.api.reconcile.ReconcilableRepository;
import org.kinotic.core.api.reconcile.Reconciler;
import org.kinotic.core.api.reconcile.Requeue;
import org.kinotic.core.api.reconcile.Watched;
import org.kinotic.core.api.reconcile.WatchedParent;
import org.kinotic.core.api.reconcile.WatchedRepository;
import org.kinotic.core.api.reconcile.WatchedType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The one place reconciliation is driven from, running as one HA cluster singleton on the Ignite
 * service grid. It knows the envelope every watched record carries and nothing of any record's
 * fields: it finds the records written since it last looked, queues each one's worker and the worker
 * of what the record belongs to, and on a longer period queues every record not in its desired
 * state. A worker is never called twice at once for one record; a record written while its worker
 * runs is queued once more when the worker returns; a worker that fails is retried with backoff; a
 * worker that asks to be called again is.
 */
@Slf4j
public class ReconcileMaster implements Service {

    private static final long TICK_MS = 2_000;
    private static final long RESYNC_MS = 300_000;
    private static final int PAGE_SIZE = 200;
    private static final long BACKOFF_MIN_MS = 5_000;
    private static final long BACKOFF_MAX_MS = 300_000;

    // Injected by Ignite on the node elected to host the singleton
    @SpringResource(resourceClass = ReconcilerRegistry.class)
    private transient ReconcilerRegistry registry;
    @SpringResource(resourceClass = Vertx.class)
    private transient Vertx vertx;

    // Guarded by this: the timers and the workers' completions arrive on Vert.x threads
    private transient Map<Key, Entry> queue;
    private transient long tickTimerId;
    private transient long resyncTimerId;

    /**
     * One record, as the master addresses it: its kind, its id, and the scope its repository stores
     * it under.
     */
    record Key(WatchedType type, String id, String scope) {
    }

    // What the master holds for a queued record: whether its worker runs, whether it changed meanwhile,
    // how many times in a row its worker failed, and the timer of a call scheduled for later
    private static final class Entry {
        boolean inFlight;
        boolean again;
        int failures;
        Long timerId;
    }

    @Override
    public void init() {
        log.info("Starting reconcile master singleton");
        // this instance was serialized to the hosting node, so runtime state is created here rather
        // than in field initializers, which do not run on deserialization
        queue = new HashMap<>();
        tick();
        resync();
        tickTimerId = vertx.setPeriodic(TICK_MS, id -> tick());
        resyncTimerId = vertx.setPeriodic(RESYNC_MS, id -> resync());
    }

    @Override
    public void execute() {
        // passive service: all work is driven by the timers started in init and the workers' completions
    }

    @Override
    public void cancel() {
        log.info("Stopping reconcile master singleton");
        vertx.cancelTimer(tickTimerId);
        vertx.cancelTimer(resyncTimerId);
        synchronized (this) {
            queue.values().forEach(entry -> {
                if (entry.timerId != null) {
                    vertx.cancelTimer(entry.timerId);
                }
            });
            queue.clear();
        }
    }

    // The trigger: every record written since the last tick queues its own worker and its parent's
    private void tick() {
        registry.repositories().forEach(this::scanDirty);
    }

    // One type parameter per repository, so the page's records are typed by the repository that read them
    private <R extends Watched> void scanDirty(WatchedRepository<R> repository) {
        repository.findDirty(Pageable.create(0, PAGE_SIZE, null))
                  .onSuccess(page -> page.getContent().forEach(record -> seen(repository, record)))
                  .onFailure(error -> log.error("Dirty scan of {} failed", repository.type(), error));
    }

    private <R extends Watched> void seen(WatchedRepository<R> repository, R record) {
        String scope = repository.scopeOf(record);
        if (registry.workerFor(repository.type()).isPresent()) {
            enqueue(new Key(repository.type(), record.getId(), scope));
        }
        // a parent lives in the same scope as what it made, which is all the master knows of it
        WatchedParent parent = record.getState().getParent();
        if (parent != null && registry.workerFor(parent.type()).isPresent()) {
            enqueue(new Key(parent.type(), parent.id(), scope));
        }
        // compare-and-clear: a write that landed between the scan and this keeps the record dirty
        repository.clearDirty(record.getId(), scope, record.getState().getDirtyAt())
                  .onFailure(error -> log.error("Could not mark {} {} as seen", repository.type(), record.getId(), error));
    }

    // The backstop: every record not in its desired state queues its worker, whether or not a write
    // was seen, so a lost tick or a worker that gave up costs one period
    private void resync() {
        registry.reconcilableRepositories().forEach(this::scanUnreconciled);
    }

    private <R extends Reconcilable<?>> void scanUnreconciled(ReconcilableRepository<R> repository) {
        repository.findUnreconciled(Pageable.create(0, PAGE_SIZE, null))
                  .onSuccess(page -> page.getContent().forEach(
                          record -> enqueue(new Key(repository.type(), record.getId(), repository.scopeOf(record)))))
                  .onFailure(error -> log.error("Resync of {} failed", repository.type(), error));
    }

    private synchronized void enqueue(Key key) {
        Entry entry = queue.computeIfAbsent(key, k -> new Entry());
        if (entry.inFlight) {
            entry.again = true;
        } else {
            // a change during a backoff or a requested wait is reason enough to look now
            if (entry.timerId != null) {
                vertx.cancelTimer(entry.timerId);
                entry.timerId = null;
            }
            run(key, entry);
        }
    }

    // Caller holds the lock. The record is re-read before every call: the worker acts on what is,
    // never on the copy the scan returned.
    private void run(Key key, Entry entry) {
        entry.inFlight = true;
        entry.again = false;
        Future<Requeue> outcome;
        try {
            outcome = current(key).compose(record -> record == null
                    ? Future.succeededFuture(Requeue.NONE)
                    : reconcile(key.type(), record));
        } catch (RuntimeException error) {
            outcome = Future.failedFuture(error);
        }
        outcome.onComplete(result -> done(key, entry, result));
    }

    private Future<Watched> current(Key key) {
        Optional<WatchedRepository<?>> repository = registry.repositoryFor(key.type());
        return repository.isPresent()
                ? find(repository.get(), key)
                : Future.failedFuture(new IllegalStateException("No repository registered for " + key.type()));
    }

    private static <R extends Watched> Future<Watched> find(WatchedRepository<R> repository, Key key) {
        return repository.find(key.id(), key.scope()).map(record -> record);
    }

    @SuppressWarnings("unchecked")
    private Future<Requeue> reconcile(WatchedType type, Watched record) {
        Optional<Reconciler<?>> worker = registry.workerFor(type);
        Future<Requeue> ret;
        if (worker.isEmpty()) {
            ret = Future.succeededFuture(Requeue.NONE);
        } else if (record instanceof Reconcilable<?> reconcilable) {
            ret = ((Reconciler<Reconcilable<?>>) worker.get()).reconcile(reconcilable);
        } else {
            ret = Future.failedFuture(new IllegalStateException(type + " has a worker but its record is not reconcilable"));
        }
        return ret;
    }

    private synchronized void done(Key key, Entry entry, AsyncResult<Requeue> result) {
        entry.inFlight = false;
        if (result.failed()) {
            entry.failures++;
            long delay = Math.min(BACKOFF_MAX_MS, BACKOFF_MIN_MS << Math.min(entry.failures - 1, 10));
            log.warn("Reconcile of {} {} failed {} time(s) in a row, next attempt in {}ms",
                     key.type(), key.id(), entry.failures, delay, result.cause());
            schedule(key, entry, delay);
        } else {
            entry.failures = 0;
            Requeue requeue = result.result();
            if (entry.again) {
                run(key, entry);
            } else if (requeue.wanted() && requeue.after().isZero()) {
                run(key, entry);
            } else if (requeue.wanted()) {
                schedule(key, entry, requeue.after().toMillis());
            } else {
                queue.remove(key);
            }
        }
    }

    // Caller holds the lock
    private void schedule(Key key, Entry entry, long delayMs) {
        entry.timerId = vertx.setTimer(Math.max(1, delayMs), id -> {
            synchronized (this) {
                entry.timerId = null;
                if (!entry.inFlight) {
                    run(key, entry);
                }
            }
        });
    }
}
