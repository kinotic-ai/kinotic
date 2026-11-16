package org.kinotic.grind;

import org.junit.jupiter.api.Test;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.StatusConditions;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.internal.api.services.JobRunLivenessWatcher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The runs a departed node leaves behind: on a change to the cluster membership every run still
 * executing on a node that is not a member is ended FAILED with SERVER_NODE_LEFT, however many
 * there are, and the runs of the member nodes keep executing.
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
