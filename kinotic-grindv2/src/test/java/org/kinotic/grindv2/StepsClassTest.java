package org.kinotic.grindv2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.grindv2.api.model.ExecutionStatus;
import org.kinotic.grindv2.api.model.JobDefinition;
import org.kinotic.grindv2.api.model.JobOwner;
import org.kinotic.grindv2.api.model.JobRunHandle;
import org.kinotic.grindv2.api.model.StoreType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link JobDefinition#fromSteps(Class)} layer: constructor injection from the
 * application context, parameter injection from the job scope, ordering, storage modes,
 * dynamic returns, wiring validation, and resume through the annotation's STATE declaration.
 */
public class StepsClassTest extends AbstractGrindV2Test {

    private StepProbe probe;

    @BeforeEach
    void registerStepBeans() {
        probe = new StepProbe();
        appCtx.getBeanFactory().registerSingleton("greetingService", new GreetingService());
        appCtx.getBeanFactory().registerSingleton("stepProbe", probe);
    }

    @Test
    public void compilesAndRunsEndToEnd() throws Exception {
        JobDefinition job = JobDefinition.fromSteps(DeploySteps.class)
                .name("deploy").version("1")
                .input(new ProjectRef("p1"));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertEquals(List.of("resolve:p1", "sync:p1-node", "ensure:hello p1-node:sha1"), probe.recorded);
        assertEquals(1, probe.instantiations.get());
        assertEquals(StoreType.STATE, repository.stepAt(handle.getJobRunId(), "0/1").getStoreType());
        assertNotNull(repository.stepAt(handle.getJobRunId(), "0/1").getResultValue());
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
        // the STATE decision replays without executing; the RESULT sync re-runs; the failed step retries
        assertEquals(List.of("sync:p1-node", "ensure:hello p1-node:sha1"), probe.recorded);
        assertEquals(2, probe.instantiations.get());
    }

    @Test
    public void stepReturningJobDefinitionExpandsDynamically() throws Exception {
        JobDefinition job = JobDefinition.fromSteps(DynamicReturnSteps.class)
                .name("dynamic-steps").version("1");

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertEquals(List.of("expanded:a", "expanded:b"), probe.recorded);
        assertEquals(ExecutionStatus.COMPLETED, repository.stepAt(handle.getJobRunId(), "0/1/1/2").getStatus());
        assertTrue(repository.stepAt(handle.getJobRunId(), "0/1").isDynamicSteps());
    }

    @Test
    public void duplicateOrdersAreRejectedAtCompile() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                                      () -> JobDefinition.fromSteps(DuplicateOrderSteps.class));
        assertTrue(error.getMessage().contains("order 1 more than once"));
    }

    @Test
    public void consumingBeforeProducingIsRejectedAtCompile() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                                      () -> JobDefinition.fromSteps(ConsumedBeforeProducedSteps.class));
        assertTrue(error.getMessage().contains("only a later step produces"));
    }

    @Test
    public void durableStateOnAValuelessStepIsRejectedAtCompile() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                                      () -> JobDefinition.fromSteps(StateOnVoidSteps.class));
        assertTrue(error.getMessage().contains("produces no storable value"));
    }

    @Test
    public void missingScopeDependencyFailsTheStepClearly() throws Exception {
        JobDefinition job = JobDefinition.fromSteps(MissingDependencySteps.class)
                .name("missing-dependency").version("1");

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNotNull(result.error());
        assertTrue(result.error().getMessage().contains("requires a WidgetState"));
    }

    private JobDefinition deployJob() {
        return JobDefinition.fromSteps(DeploySteps.class)
                .name("deploy").version("1")
                .input(new ProjectRef("p1"));
    }

}
