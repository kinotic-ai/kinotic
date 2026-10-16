package org.kinotic.grind;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobScope;
import org.kinotic.grind.api.model.TaskReporter;
import org.kinotic.grind.api.model.events.JobRunEvent;
import org.kinotic.grind.api.model.events.TaskCompletedEvent;
import org.kinotic.grind.api.model.events.TaskProgressEvent;
import org.kinotic.grind.api.model.events.TaskStartedEvent;
import org.kinotic.grind.api.model.Tasks;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Progress reporting: a task injects the scope's {@link TaskReporter}, and each report
 * reaches watchers as a {@link TaskProgressEvent} attributed to the reporting task.
 */
public class ProgressTest extends AbstractGrindTest {

    @Test
    public void reportsFlowBetweenTheTasksStartAndCompletion() throws Exception {
        JobDefinition job = JobDefinition.create("measured")
                .name("measured").version("1")
                .task(Tasks.fromCallable("pull image", new Callable<Void>() {

                    @Autowired
                    private TaskReporter progress;

                    @Override
                    public Void call() {
                        progress.progress(30, "downloading layers");
                        progress.progress(80, "extracting");
                        return null;
                    }
                }));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        List<JobRunEvent> events = result.events();
        assertEquals(List.of(new TaskProgressEvent("0/1", 30, "downloading layers", null),
                             new TaskProgressEvent("0/1", 80, "extracting", null)),
                     events.stream().filter(TaskProgressEvent.class::isInstance).toList());
        int started = events.indexOf(new TaskStartedEvent("0/1", "pull image", null));
        int firstReport = events.indexOf(new TaskProgressEvent("0/1", 30, "downloading layers", null));
        int completed = indexOfCompletion(events, "0/1");
        assertTrue(started < firstReport && firstReport < completed);
    }

    @Test
    public void annotatedTaskInjectsTheReporterAsAParameter() throws Exception {
        JobDefinition job = JobDefinition.fromTasks(ProgressTasks.class).name("progress-tasks").version("1");

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        assertEquals(List.of(new TaskProgressEvent("0/1", 50, "halfway", null)),
                     result.events().stream().filter(TaskProgressEvent.class::isInstance).toList());
    }

    @Test
    public void parallelSiblingsReportUnderTheirOwnPaths() throws Exception {
        JobDefinition job = JobDefinition.create("racing", JobScope.CHILD, true)
                .name("racing").version("1")
                .task(reportingTask("left", 10))
                .task(reportingTask("right", 20));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        List<TaskProgressEvent> reports = result.events().stream()
                                                .filter(TaskProgressEvent.class::isInstance)
                                                .map(TaskProgressEvent.class::cast)
                                                .toList();
        assertEquals(2, reports.size());
        assertTrue(reports.contains(new TaskProgressEvent("0/1", 10, "left", null)));
        assertTrue(reports.contains(new TaskProgressEvent("0/2", 20, "right", null)));
    }

    @Test
    public void reportsFromCallbacksOfTheTasksFuturesAttachToTheTask() throws Exception {
        JobDefinition job = JobDefinition.create("timed")
                .name("timed").version("1")
                .task(Tasks.fromCallable("wait", new Callable<Future<String>>() {

                    @Autowired
                    private TaskReporter progress;

                    @Override
                    public Future<String> call() {
                        return vertx.timer(10)
                                    .map(v -> "ticked")
                                    .onSuccess(value -> progress.progress(100, value));
                    }
                }));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        assertEquals(List.of(new TaskProgressEvent("0/1", 100, "ticked", null)),
                     result.events().stream().filter(TaskProgressEvent.class::isInstance).toList());
    }

    @Test
    public void reportsFromAThreadTheTaskSpawnedAreDropped() throws Exception {
        AtomicReference<Throwable> spawnedError = new AtomicReference<>();
        JobDefinition job = JobDefinition.create("spawning")
                .name("spawning").version("1")
                .task(Tasks.fromCallable("spawn", new Callable<Void>() {

                    @Autowired
                    private TaskReporter progress;

                    @Override
                    public Void call() throws InterruptedException {
                        Thread spawned = new Thread(() -> progress.progress(50, "from elsewhere"));
                        spawned.setUncaughtExceptionHandler((thread, error) -> spawnedError.set(error));
                        spawned.start();
                        spawned.join();
                        return null;
                    }
                }));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        assertNull(spawnedError.get());
        assertTrue(result.events().stream().noneMatch(TaskProgressEvent.class::isInstance));
    }

    private org.kinotic.grind.api.model.Task<Void> reportingTask(String message, int percentage) {
        return Tasks.fromCallable(message, new Callable<>() {

            @Autowired
            private TaskReporter progress;

            @Override
            public Void call() {
                progress.progress(percentage, message);
                return null;
            }
        });
    }

    private int indexOfCompletion(List<JobRunEvent> events, String taskPath) {
        int ret = -1;
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i) instanceof TaskCompletedEvent completed && completed.taskPath().equals(taskPath)) {
                ret = i;
            }
        }
        return ret;
    }

}
