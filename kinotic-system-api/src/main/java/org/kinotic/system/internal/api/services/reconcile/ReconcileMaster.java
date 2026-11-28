package org.kinotic.system.internal.api.services.reconcile;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.resources.SpringResource;
import org.apache.ignite.services.Service;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Reconcilable;
import org.kinotic.domain.api.repositories.ReconcilableRepository;
import org.kinotic.domain.api.services.Reconciler;
import org.kinotic.domain.api.model.Requeue;
import org.kinotic.domain.api.model.Watched;
import org.kinotic.domain.api.model.WatchedParent;
import org.kinotic.domain.api.repositories.WatchedRepository;
import org.kinotic.domain.api.model.WatchedType;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The one place reconciliation is driven from, running as one HA cluster singleton on the Ignite
 * service grid. It knows the envelope every watched record carries and nothing of any record's
 * fields: it finds the records written since it last looked, queues each one's worker and the worker
 * of what the record belongs to, and on a longer period queues every record not in its desired
 * state. When it starts it queues every reconcilable record once, so a worker that keeps its own
 * timer going, such as the node's heartbeat watch, starts it on the node now hosting the master. A
 * worker is never called twice at once for one record; a record written while its worker runs is
 * queued once more when the worker returns; a worker that fails is retried with backoff; a worker
 * that asks to be called again is. At most {@link #MAX_IN_FLIGHT} workers run at once, and the rest
 * wait their turn, so a scan that finds many records, a resync of many, or the initial list reaches
 * the store at a bounded rate rather than all at once. Only the calls of workers are traced, each
 * starting a trace of its own whose root span is named {@code reconcile <type>}, such as
 * {@code reconcile VM_NODE}; the periodic scans that find the records to queue are untraced.
 */
@Slf4j
public class ReconcileMaster implements Service {

    private static final long TICK_MS = 2_000;
    private static final long RESYNC_MS = 300_000;
    private static final int PAGE_SIZE = 200;
    private static final long BACKOFF_MIN_MS = 5_000;
    private static final long BACKOFF_MAX_MS = 300_000;
    private static final int MAX_IN_FLIGHT = 32;

    private static final String INSTRUMENTATION_NAME = "org.kinotic.system.reconcile";
    private static final AttributeKey<String> RECORD_TYPE = AttributeKey.stringKey("kinotic.reconcile.type");
    private static final AttributeKey<String> RECORD_ID = AttributeKey.stringKey("kinotic.reconcile.id");
    private static final AttributeKey<String> RECORD_SCOPE = AttributeKey.stringKey("kinotic.reconcile.scope");

    // Injected by Ignite on the node elected to host the singleton
    @SpringResource(resourceClass = ReconcilerRegistry.class)
    private transient ReconcilerRegistry registry;
    @SpringResource(resourceClass = Vertx.class)
    private transient Vertx vertx;
    @SpringResource(resourceClass = OpenTelemetry.class)
    private transient OpenTelemetry openTelemetry;

    private transient Tracer tracer;

    // Guarded by this: the timers and the workers' completions arrive on Vert.x threads
    private transient Map<Key, Entry> queue;
    // The keys whose turn has not come, in the order they were queued; also guarded by this
    private transient ArrayDeque<Key> waiting;
    private transient int inFlight;
    private transient long tickTimerId;
    private transient long resyncTimerId;

    /**
     * One record, as the master addresses it: its kind, its id, and the scope its repository stores
     * it under.
     */
    record Key(WatchedType type, String id, String scope) {
    }

    // What the master holds for a queued record: whether its worker runs or waits its turn, whether it
    // changed meanwhile, how many times in a row its worker failed, and the timer of a call scheduled
    // for later
    private static final class Entry {
        boolean inFlight;
        boolean waiting;
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
        waiting = new ArrayDeque<>();
        tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME);
        registry.reconcilableRepositories().forEach(repository -> enqueueAll(repository, 0));
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
            waiting.clear();
        }
    }

    // The trigger: every record written since the last tick queues its own worker and its parent's
    private void tick() {
        try (Scope ignored = unsampled().makeCurrent()) {
            registry.repositories().forEach(this::scanDirty);
        }
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
        WatchedParent parent = record.getState().getParent();
        if (parent != null && registry.workerFor(parent.type()).isPresent()) {
            enqueue(new Key(parent.type(), parent.id(), parent.scope()));
        }
        // compare-and-clear: a write that landed between the scan and this keeps the record dirty
        repository.clearDirty(record)
                  .onFailure(error -> log.error("Could not mark {} {} as seen", repository.type(), record.getId(), error));
    }

    // The backstop: every record not in its desired state queues its worker, whether or not a write
    // was seen, so a lost tick or a worker that gave up costs one period
    private void resync() {
        try (Scope ignored = unsampled().makeCurrent()) {
            registry.reconcilableRepositories().forEach(this::scanUnreconciled);
        }
    }

    private <R extends Reconcilable<?>> void scanUnreconciled(ReconcilableRepository<R> repository) {
        repository.findUnreconciled(Pageable.create(0, PAGE_SIZE, null))
                  .onSuccess(page -> page.getContent().forEach(
                          record -> enqueue(new Key(repository.type(), record.getId(), repository.scopeOf(record)))))
                  .onFailure(error -> log.error("Resync of {} failed", repository.type(), error));
    }

    // A valid parent that is not sampled, so the agent's parent-based sampler drops every span the scans'
    // Elasticsearch calls start under it. Its ids are random because they reach Elasticsearch in the
    // traceparent header; the lower bound of 1 keeps them off the all-zero id, which is invalid.
    private static Context unsampled() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return Context.root().with(Span.wrap(SpanContext.create(TraceId.fromLongs(random.nextLong(1, Long.MAX_VALUE), random.nextLong()),
                                                                SpanId.fromLong(random.nextLong(1, Long.MAX_VALUE)),
                                                                TraceFlags.getDefault(),
                                                                TraceState.getDefault())));
    }

    // The one look at every record: page by page, since a kind may hold more than a scan reads
    private <R extends Reconcilable<?>> void enqueueAll(ReconcilableRepository<R> repository, int pageNumber) {
        repository.findAll(Pageable.create(pageNumber, PAGE_SIZE, null))
                  .onSuccess(page -> {
                      page.getContent().forEach(record -> enqueue(new Key(repository.type(), record.getId(), repository.scopeOf(record))));
                      if (page.getContent().size() == PAGE_SIZE) {
                          enqueueAll(repository, pageNumber + 1);
                      }
                  })
                  .onFailure(error -> log.error("Initial list of {} failed", repository.type(), error));
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
            start(key, entry);
        }
    }

    // Caller holds the lock. Runs the worker when a slot is free, otherwise takes a place in line once.
    private void start(Key key, Entry entry) {
        if (inFlight < MAX_IN_FLIGHT) {
            run(key, entry);
        } else if (!entry.waiting) {
            entry.waiting = true;
            waiting.add(key);
        }
    }

    // Caller holds the lock. The record is re-read before every call: the worker acts on what is,
    // never on the copy the scan returned.
    private void run(Key key, Entry entry) {
        entry.inFlight = true;
        entry.waiting = false;
        entry.again = false;
        inFlight++;
        // The span's parent is the root context because run is reached from timers and completion
        // callbacks that carry the previous call's context, which would otherwise chain every call of a
        // record's worker into one endless trace
        Span span = tracer.spanBuilder("reconcile " + key.type())
                          .setParent(Context.root())
                          .setAttribute(RECORD_TYPE, key.type().name())
                          .setAttribute(RECORD_ID, key.id())
                          .setAttribute(RECORD_SCOPE, key.scope())
                          .startSpan();
        Future<Requeue> outcome;
        try (Scope ignored = span.makeCurrent()) {
            outcome = current(key).compose(record -> record == null
                    ? Future.succeededFuture(Requeue.NONE)
                    : reconcile(key.type(), record));
        } catch (RuntimeException error) {
            outcome = Future.failedFuture(error);
        }
        outcome.onComplete(result -> {
            if (result.failed()) {
                span.recordException(result.cause());
                span.setStatus(StatusCode.ERROR);
            }
            span.end();
            done(key, entry, result);
        });
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
        inFlight--;
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
                start(key, entry);
            } else if (requeue.wanted() && requeue.after().isZero()) {
                start(key, entry);
            } else if (requeue.wanted()) {
                schedule(key, entry, requeue.after().toMillis());
            } else {
                queue.remove(key);
            }
        }
        // the freed slot goes to the next in line; a key removed or started meanwhile has left the line
        while (inFlight < MAX_IN_FLIGHT && !waiting.isEmpty()) {
            Key next = waiting.poll();
            Entry queued = queue.get(next);
            if (queued != null && queued.waiting && !queued.inFlight) {
                run(next, queued);
            }
        }
    }

    // Caller holds the lock
    private void schedule(Key key, Entry entry, long delayMs) {
        entry.timerId = vertx.setTimer(Math.max(1, delayMs), id -> {
            synchronized (this) {
                entry.timerId = null;
                if (!entry.inFlight) {
                    start(key, entry);
                }
            }
        });
    }
}
