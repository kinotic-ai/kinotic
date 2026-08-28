package org.kinotic.grind;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.StoreType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link JobDefinition#fromTasks(Class)} layer: constructor injection from the
 * application context, parameter injection from the job scope, ordering, storage modes,
 * dynamic returns, wiring validation, and resume through the annotation's STATE declaration.
 */
public class TaskClassTest extends AbstractGrindTest {

    private TaskProbe probe;

    @BeforeEach
    void registerTaskBeans() {
        probe = new TaskProbe();
        appCtx.getBeanFactory().registerSingleton("greetingService", new GreetingService());
        appCtx.getBeanFactory().registerSingleton("taskProbe", probe);
    }

    @Test
    public void compilesAndRunsEndToEnd() throws Exception {
        JobDefinition job = JobDefinition.fromTasks(DeployTasks.class)
                .name("deploy").version("1")
                .input(new ProjectRef("p1"));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertEquals(List.of("resolve:p1", "sync:p1-node", "ensure:hello p1-node:sha1"), probe.recorded);
        assertEquals(1, probe.instantiations.get());
        assertEquals(StoreType.STATE, repository.taskAt(handle.getJobRunId(), "0/1").getStoreType());
        assertNotNull(repository.taskAt(handle.getJobRunId(), "0/1").getResultValue());
        assertEquals(ExecutionStatus.COMPLETED, repository.savedRuns.get(handle.getJobRunId()).getStatus());
    }

    @Test
    public void annotatedStateReplaysOnResume() throws Exception {
        probe.failNext.set(true);
        JobRunHandle original = jobService.run(deployJob(), JobOwner.system());
        assertNotNull(await(original).error());
        assertEquals(List.of("resolve:p1", "sync:p1-node", "ensure:hello p1-node:sha1"), probe.recorded);

        probe.failNext.set(false);
        probe.recorded.clear();
        RunResult resumed = await(jobService.resume(original.getJobRunId(), deployJob()));

        assertNull(resumed.error());
        // the STATE decision replays without executing; the RESULT sync re-runs; the failed task retries
        assertEquals(List.of("sync:p1-node", "ensure:hello p1-node:sha1"), probe.recorded);
        assertEquals(2, probe.instantiations.get());
    }

    @Test
    public void taskReturningJobDefinitionExpandsDynamically() throws Exception {
        JobDefinition job = JobDefinition.fromTasks(DynamicReturnTasks.class)
                .name("dynamic-tasks").version("1");

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertEquals(List.of("expanded:a", "expanded:b"), probe.recorded);
        assertEquals(ExecutionStatus.COMPLETED, repository.taskAt(handle.getJobRunId(), "0/1/1/2").getStatus());
        assertTrue(repository.taskAt(handle.getJobRunId(), "0/1").isDynamicTasks());
    }

    @Test
    public void duplicateOrdersAreRejectedAtCompile() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                                      () -> JobDefinition.fromTasks(DuplicateOrderTasks.class));
        assertTrue(error.getMessage().contains("order 1 more than once"));
    }

    @Test
    public void consumingBeforeProducingIsRejectedAtCompile() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                                      () -> JobDefinition.fromTasks(ConsumedBeforeProducedTasks.class));
        assertTrue(error.getMessage().contains("only a later task produces"));
    }

    @Test
    public void durableStateOnAValuelessTaskIsRejectedAtCompile() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                                      () -> JobDefinition.fromTasks(StateOnVoidTasks.class));
        assertTrue(error.getMessage().contains("produces no storable value"));
    }

    @Test
    public void missingScopeDependencyFailsTheTaskClearly() throws Exception {
        JobDefinition job = JobDefinition.fromTasks(MissingDependencyTasks.class)
                .name("missing-dependency").version("1");

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNotNull(result.error());
        assertTrue(result.error().getMessage().contains("requires a WidgetState"));
    }

    private JobDefinition deployJob() {
        return JobDefinition.fromTasks(DeployTasks.class)
                .name("deploy").version("1")
                .input(new ProjectRef("p1"));
    }

}
