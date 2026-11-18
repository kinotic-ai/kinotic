package org.kinotic.grind;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.grind.api.model.JobContext;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.Task;
import org.kinotic.grind.api.model.Tasks;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One {@link JobDefinition} serving more than one run.
 */
public class ReusedDefinitionTest extends AbstractGrindTest {

    private static final int RUNS = 4;

    @Test
    public void runsAtTheSameTimeEachInjectTheirOwnValues() throws Exception {
        AtomicInteger made = new AtomicInteger();
        List<String> held = Collections.synchronizedList(new ArrayList<>());
        JobDefinition job = JobDefinition.create("reused")
                .name("reused").version("1")
                .task(Tasks.fromCallable("make widget", () -> new Widget("w" + made.incrementAndGet())).store(Store.result()))
                .task(Tasks.fromCallable("hold widget", new Callable<Future<Void>>() {

                    @Autowired
                    private Widget widget;

                    @Autowired
                    private JobContext context;

                    @Override
                    public Future<Void> call() {
                        // the other runs reach this task while this one waits
                        return vertx.timer(200)
                                    .onSuccess(id -> held.add(widget.name() + " in the run of " + context.getBean(Widget.class).name()))
                                    .mapEmpty();
                    }
                }));

        CountDownLatch ended = new CountDownLatch(RUNS);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < RUNS; i++) {
            JobRunHandle handle = jobService.run(job, JobOwner.system());
            handle.getEvents().subscribe(event -> {}, error -> {
                errors.add(error);
                ended.countDown();
            }, ended::countDown);
        }

        assertTrue(ended.await(15, TimeUnit.SECONDS), "the runs did not end within 15s");
        assertEquals(List.of(), errors);
        assertEquals(Set.of("w1 in the run of w1", "w2 in the run of w2", "w3 in the run of w3", "w4 in the run of w4"),
                     Set.copyOf(held));
    }

    @Test
    public void everyFormOfInlineTaskInjectsEachRun() throws Exception {
        AtomicInteger made = new AtomicInteger();
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        String prefix = "enclosing instance ";
        JobDefinition job = JobDefinition.create("forms")
                .name("forms").version("1")
                .task(Tasks.fromCallable("make widget", () -> new Widget("w" + made.incrementAndGet())).store(Store.result()))
                .task(Tasks.fromRunnable("uses its enclosing instance", new Runnable() {

                    @Autowired
                    private Widget widget;

                    @Override
                    public void run() {
                        seen.add(prefix + widget.name() + " on " + jobService.getClass().getSimpleName());
                    }
                }))
                .task(neverUsesItsEnclosingInstance(seen, "never uses it ", 7))
                .task(declaredInAStaticMethod(seen))
                .task(injectedThroughAMethod(seen))
                .task(new AbstractWidgetSteps() {}.recordWidget(seen));

        assertNull(await(jobService.run(job, JobOwner.system())).error());
        assertNull(await(jobService.run(job, JobOwner.system())).error());

        List<String> expected = new ArrayList<>();
        for (String widget : List.of("w1", "w2")) {
            expected.add("enclosing instance " + widget + " on DefaultJobService");
            expected.add("never uses it " + widget + " 7");
            expected.add("static method " + widget);
            expected.add("method " + widget);
            expected.add("abstract class " + widget);
        }
        assertEquals(expected, seen);
    }

    @Test
    public void anInjectedInstanceOfANamedClassIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Tasks.fromCallable("named", new WidgetNameCallable()));
    }

    private Task<Void> neverUsesItsEnclosingInstance(List<String> seen, String prefix, int count) {
        return Tasks.fromRunnable("never uses its enclosing instance", new Runnable() {

            @Autowired
            private Widget widget;

            @Override
            public void run() {
                seen.add(prefix + widget.name() + " " + count);
            }
        });
    }

    private static Task<Void> declaredInAStaticMethod(List<String> seen) {
        return Tasks.fromRunnable("declared in a static method", new Runnable() {

            @Autowired
            private Widget widget;

            @Override
            public void run() {
                seen.add("static method " + widget.name());
            }
        });
    }

    private static Task<Void> injectedThroughAMethod(List<String> seen) {
        return Tasks.fromRunnable("injected through a method", new Runnable() {

            private Widget widget;

            @Autowired
            void setWidget(Widget widget) {
                this.widget = widget;
            }

            @Override
            public void run() {
                seen.add("method " + widget.name());
            }
        });
    }

}
