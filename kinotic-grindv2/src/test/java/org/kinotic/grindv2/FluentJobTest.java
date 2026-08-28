package org.kinotic.grindv2;

import org.junit.jupiter.api.Test;
import org.kinotic.grindv2.api.model.ExecutionStatus;
import org.kinotic.grindv2.api.model.JobContext;
import org.kinotic.grindv2.api.model.JobDefinition;
import org.kinotic.grindv2.api.model.JobOwner;
import org.kinotic.grindv2.api.model.JobScope;
import org.kinotic.grindv2.api.model.Store;
import org.kinotic.grindv2.api.model.Tasks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The fluent authoring surface: ordering, scope storage and injection, nesting, inputs, and
 * asynchronous task results.
 */
public class FluentJobTest extends AbstractGrindV2Test {

    @Test
    public void executesTasksInDeclaredOrder() throws Exception {
        List<String> seen = new ArrayList<>();
        JobDefinition job = JobDefinition.create("ordered")
                .name("ordered").version("1")
                .task(Tasks.fromRunnable("one", () -> seen.add("one")))
                .task(Tasks.fromRunnable("two", () -> seen.add("two")))
                .task(Tasks.fromRunnable("three", () -> seen.add("three")));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        assertEquals(List.of("one", "two", "three"), seen);
        assertEquals(ExecutionStatus.COMPLETED, repository.savedRuns.values().iterator().next().getStatus());
    }

    @Test
    public void storedResultInjectsByType() throws Exception {
        AtomicReference<Widget> seen = new AtomicReference<>();
        JobDefinition job = JobDefinition.create("inject")
                .name("inject").version("1")
                .task(Tasks.fromValue("produce widget", new Widget("w1")), Store.result())
                .task(Tasks.fromCallable("consume widget", new Callable<Void>() {

                    @Autowired
                    private Widget widget;

                    @Override
                    public Void call() {
                        seen.set(widget);
                        return null;
                    }
                }));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        assertEquals(new Widget("w1"), seen.get());
    }

    @Test
    public void namedResultResolvesValuePlaceholder() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        JobDefinition job = JobDefinition.create("placeholders")
                .name("placeholders").version("1")
                .task(Tasks.fromValue("produce greeting", "hello grind"), Store.result("greeting"))
                .task(Tasks.fromCallable("consume greeting", new Callable<Void>() {

                    @Value("${greeting}")
                    private String greeting;

                    @Override
                    public Void call() {
                        seen.set(greeting);
                        return null;
                    }
                }));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        assertEquals("hello grind", seen.get());
    }

    @Test
    public void childScopeValuesAreDiscarded() throws Exception {
        AtomicReference<Widget> seenAfterNested = new AtomicReference<>(new Widget("sentinel"));
        JobDefinition nested = JobDefinition.create("nested child", JobScope.CHILD)
                .task(Tasks.fromValue("produce widget", new Widget("scoped")), Store.result());
        JobDefinition job = JobDefinition.create("child scope")
                .name("child-scope").version("1")
                .jobDefinition(nested)
                .task(probe(seenAfterNested));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        assertNull(seenAfterNested.get());
    }

    @Test
    public void parentScopeValuesRemainVisible() throws Exception {
        AtomicReference<Widget> seenAfterNested = new AtomicReference<>();
        JobDefinition nested = JobDefinition.create("nested parent", JobScope.PARENT)
                .task(Tasks.fromValue("produce widget", new Widget("shared")), Store.result());
        JobDefinition job = JobDefinition.create("parent scope")
                .name("parent-scope").version("1")
                .jobDefinition(nested)
                .task(probe(seenAfterNested));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        assertEquals(new Widget("shared"), seenAfterNested.get());
    }

    @Test
    public void inputsSeedTheScope() throws Exception {
        AtomicReference<Widget> seen = new AtomicReference<>();
        JobDefinition job = JobDefinition.create("inputs")
                .name("inputs").version("1")
                .input(new Widget("seeded"))
                .task(probe(seen));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        assertEquals(new Widget("seeded"), seen.get());
    }

    @Test
    public void awaitsAsynchronousTaskResults() throws Exception {
        AtomicReference<Widget> seen = new AtomicReference<>();
        JobDefinition job = JobDefinition.create("async")
                .name("async").version("1")
                .task(Tasks.fromCallable("produce later",
                                         () -> CompletableFuture.supplyAsync(
                                                 () -> new Widget("eventually"),
                                                 CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS,
                                                                                   Executors.newSingleThreadExecutor()))),
                      Store.result())
                .task(probe(seen));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        assertEquals(new Widget("eventually"), seen.get());
    }

    private org.kinotic.grindv2.api.model.Task<Void> probe(AtomicReference<Widget> target) {
        return new org.kinotic.grindv2.api.model.Task<>() {
            @Override
            public String getDescription() {
                return "probe widget";
            }

            @Override
            public Void execute(JobContext context) {
                target.set(context.getBeanOrNull(Widget.class));
                return null;
            }
        };
    }

}
