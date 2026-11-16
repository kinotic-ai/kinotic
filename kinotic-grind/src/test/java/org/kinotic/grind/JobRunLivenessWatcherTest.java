package org.kinotic.grind;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.StatusConditions;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.internal.api.services.JobRunLivenessWatcher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The runs a departed node leaves behind: on a change to the cluster membership every run still
 * executing on a node that is not a member is ended FAILED with SERVER_NODE_LEFT, however many
 * there are, and the runs of the member nodes keep executing, those of a node that joins while the
 * departed node's runs are being ended among them. A search that fails is made again, and a run
 * the first search did not hold is ended by the second.
 */
public class JobRunLivenessWatcherTest extends AbstractGrindTest {

    private static final String DEPARTED_NODE_ID = "departed-node";

    // more than two of the watcher's pages, so the last run is reached only by reading on
    private static final int DEPARTED_RUNS = 450;

    @Test
    public void everyRunOfADepartedNodeIsEndedAndAMembersRunIsLeftExecuting() throws Exception {
        for (int i = 0; i < DEPARTED_RUNS; i++) {
            repository.saveRun(executing("departed-" + i, DEPARTED_NODE_ID));
        }
        repository.saveRun(executing("member", TEST_NODE_ID));
        Sinks.Many<Set<String>> membership = Sinks.many().replay().latest();

        try (AnnotationConfigApplicationContext watcherCtx = new AnnotationConfigApplicationContext()) {
            watcherCtx.getBeanFactory().registerSingleton("eventBusService", eventBusReporting(membership.asFlux()));
            watcherCtx.getBeanFactory().registerSingleton("jobRunRepository", repository);
            watcherCtx.getBeanFactory().registerSingleton("vertx", vertx);
            watcherCtx.register(JobRunLivenessWatcher.class);
            watcherCtx.refresh();

            membership.tryEmitNext(Set.of(TEST_NODE_ID));

            awaitUntil("the departed node's runs to end", () -> departedRuns().stream().noneMatch(JobRunLivenessWatcherTest::isExecuting));
        }

        List<JobRun> departed = departedRuns();
        assertEquals(DEPARTED_RUNS, departed.size());
        for (JobRun run : departed) {
            assertEquals(ExecutionStatus.FAILED, run.getStatus());
            assertNotNull(run.getFinished());
            assertEquals("Node " + DEPARTED_NODE_ID + " left the cluster while the run was live", run.getError());
            assertTrue(StatusConditions.has(run.getState().getConditions(), StatusConditionType.SERVER_NODE_LEFT));
        }
        JobRun member = repository.savedRuns.get("member");
        assertEquals(ExecutionStatus.RUNNING, member.getStatus());
        assertNull(member.getFinished());
        assertFalse(StatusConditions.has(member.getState().getConditions(), StatusConditionType.SERVER_NODE_LEFT));
    }

    @Test
    public void aNodeThatJoinsWhileRunsAreBeingEndedKeepsItsRun() throws Exception {
        String joinedNodeId = "joined-node";
        // delivers to the watcher inside the emit, so the watcher knows of the join before the write below is made
        Sinks.Many<Set<String>> membership = Sinks.many().multicast().directBestEffort();
        List<String> writes = new CopyOnWriteArrayList<>();
        AtomicBoolean joined = new AtomicBoolean();
        // a store that sees the node join as the first run is about to be ended
        InMemoryJobRunRepository ledger = new InMemoryJobRunRepository() {
            @Override
            public Future<Boolean> recordAbandoned(String jobRunId, StatusCondition condition, String source) {
                if (joined.compareAndSet(false, true)) {
                    membership.emitNext(Set.of(TEST_NODE_ID, joinedNodeId), Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(5)));
                }
                Future<Boolean> ret = super.recordAbandoned(jobRunId, condition, source);
                // counted once the write is applied, so a counted write can be read
                writes.add(jobRunId);
                return ret;
            }
        };
        ledger.saveRun(executing("departed-first", DEPARTED_NODE_ID));
        ledger.saveRun(executing("joined", joinedNodeId));
        ledger.saveRun(executing("departed-last", DEPARTED_NODE_ID));

        try (AnnotationConfigApplicationContext watcherCtx = new AnnotationConfigApplicationContext()) {
            watcherCtx.getBeanFactory().registerSingleton("eventBusService", eventBusReporting(membership.asFlux()));
            watcherCtx.getBeanFactory().registerSingleton("jobRunRepository", ledger);
            watcherCtx.getBeanFactory().registerSingleton("vertx", vertx);
            watcherCtx.register(JobRunLivenessWatcher.class);
            watcherCtx.refresh();

            membership.emitNext(Set.of(TEST_NODE_ID), Sinks.EmitFailureHandler.FAIL_FAST);

            // the join starts a second pass over the departed node's runs; the pass that searched before
            // the join comes to the last run only after the joined node's
            awaitUntil("both passes to reach the last run", () -> Collections.frequency(writes, "departed-last") >= 2);
        }

        assertEquals(ExecutionStatus.FAILED, ledger.savedRuns.get("departed-first").getStatus());
        assertEquals(ExecutionStatus.FAILED, ledger.savedRuns.get("departed-last").getStatus());
        JobRun joinedRun = ledger.savedRuns.get("joined");
        assertEquals(ExecutionStatus.RUNNING, joinedRun.getStatus());
        assertNull(joinedRun.getFinished());
    }

    @Test
    public void aSearchThatFailsIsMadeAgain() throws Exception {
        AtomicInteger searches = new AtomicInteger();
        InMemoryJobRunRepository ledger = new InMemoryJobRunRepository() {
            @Override
            public synchronized Future<Page<JobRun>> findRunningElsewhere(Set<String> members, Pageable pageable) {
                Future<Page<JobRun>> ret;
                if (searches.incrementAndGet() == 1) {
                    ret = Future.failedFuture(new IllegalStateException("store is down"));
                } else {
                    ret = super.findRunningElsewhere(members, pageable);
                }
                return ret;
            }
        };
        ledger.saveRun(executing("departed", DEPARTED_NODE_ID));

        awaitEnded(ledger, "departed");
    }

    @Test
    public void aRunTheFirstSearchDidNotHoldIsEndedByTheSecond() throws Exception {
        AtomicInteger searches = new AtomicInteger();
        // a store whose search does not hold the run yet, as one made before the index refreshed
        InMemoryJobRunRepository ledger = new InMemoryJobRunRepository() {
            @Override
            public synchronized Future<Page<JobRun>> findRunningElsewhere(Set<String> members, Pageable pageable) {
                Future<Page<JobRun>> ret;
                if (searches.incrementAndGet() == 1) {
                    ret = Future.succeededFuture(new Page<>(List.of(), 0L));
                } else {
                    ret = super.findRunningElsewhere(members, pageable);
                }
                return ret;
            }
        };
        ledger.saveRun(executing("departed", DEPARTED_NODE_ID));

        awaitEnded(ledger, "departed");
    }

    // Reports a membership the departed node is not part of to a watcher over the ledger, and waits for the run to be ended
    private void awaitEnded(InMemoryJobRunRepository ledger, String jobRunId) throws InterruptedException {
        Sinks.Many<Set<String>> membership = Sinks.many().replay().latest();
        try (AnnotationConfigApplicationContext watcherCtx = new AnnotationConfigApplicationContext()) {
            watcherCtx.getBeanFactory().registerSingleton("eventBusService", eventBusReporting(membership.asFlux()));
            watcherCtx.getBeanFactory().registerSingleton("jobRunRepository", ledger);
            watcherCtx.getBeanFactory().registerSingleton("vertx", vertx);
            watcherCtx.register(JobRunLivenessWatcher.class);
            watcherCtx.refresh();

            membership.tryEmitNext(Set.of(TEST_NODE_ID));

            awaitUntil("the departed node's run to end", () -> ledger.savedRuns.get(jobRunId).getStatus() == ExecutionStatus.FAILED);
        }
    }

    private List<JobRun> departedRuns() {
        List<JobRun> ret;
        synchronized (repository) {
            ret = repository.savedRuns.values().stream()
                                      .filter(run -> DEPARTED_NODE_ID.equals(run.getNodeId()))
                                      .toList();
        }
        return ret;
    }

    private static boolean isExecuting(JobRun run) {
        return run.getStatus() == ExecutionStatus.RUNNING;
    }

    private static JobRun executing(String id, String nodeId) {
        return new JobRun().setId(id)
                           .setName("liveness-run")
                           .setNodeId(nodeId);
    }

    // The watcher reads the cluster membership from the event bus; the bean factory asks it for its identity
    private static EventBusService eventBusReporting(Flux<Set<String>> membership) {
        return (EventBusService) Proxy.newProxyInstance(EventBusService.class.getClassLoader(),
                                                        new Class<?>[]{EventBusService.class},
                                                        (proxy, method, args) -> switch (method.getName()) {
                                                            case "monitorClusterNodes" -> membership;
                                                            case "hashCode" -> System.identityHashCode(proxy);
                                                            case "equals" -> proxy == args[0];
                                                            case "toString" -> "event bus reporting a test's membership";
                                                            default -> throw new UnsupportedOperationException(method.getName());
                                                        });
    }

}
