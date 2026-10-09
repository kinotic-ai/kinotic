package org.kinotic.grind;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.JobScope;
import org.kinotic.grind.api.model.Task;
import org.kinotic.grind.api.model.TaskLogEntry;
import org.kinotic.grind.api.model.TaskLogLevel;
import org.kinotic.grind.api.model.TaskLogger;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.grind.api.model.events.JobRunEvent;
import org.kinotic.grind.api.model.events.TaskCompletedEvent;
import org.kinotic.grind.api.model.events.TaskLogEvent;
import org.kinotic.grind.api.model.events.TaskStartedEvent;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task logs: a task injects the scope's {@link TaskLogger}, each line reaches watchers as a
 * {@link TaskLogEvent} attributed to the writing task, and the task's record keeps its lines once
 * it finishes.
 */
public class TaskLogTest extends AbstractGrindTest {

    @Test
    public void linesFlowBetweenTheTasksStartAndCompletionAndAreRecorded() throws Exception {
        JobDefinition job = JobDefinition.create("swept")
                .name("swept").version("1")
                .task(Tasks.fromCallable("sweep", new Callable<Void>() {

                    @Autowired
                    private TaskLogger logger;

                    @Override
                    public Void call() {
                        logger.info("Found {} workloads", 3);
                        logger.warn("Workload {} is still running", "wl-1");
                        return null;
                    }
                }));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        List<JobRunEvent> events = result.events();
        List<TaskLogEvent> lines = linesOf(events);
        assertEquals(List.of("0/1", "0/1"), lines.stream().map(TaskLogEvent::taskPath).toList());
        assertEquals(List.of(TaskLogLevel.INFO, TaskLogLevel.WARN), lines.stream().map(line -> line.entry().getLevel()).toList());
        assertEquals(List.of("Found 3 workloads", "Workload wl-1 is still running"),
                     lines.stream().map(line -> line.entry().getMessage()).toList());
        assertNotNull(lines.getFirst().entry().getTimestamp());

        int started = events.indexOf(new TaskStartedEvent("0/1", "sweep"));
        int firstLine = events.indexOf(lines.getFirst());
        int completed = indexOfCompletion(events, "0/1");
        assertTrue(started < firstLine && firstLine < completed);

        TaskRecord record = repository.taskAt(handle.getJobRunId(), "0/1");
        assertEquals(lines.stream().map(TaskLogEvent::entry).toList(), record.getLogs());
    }

    @Test
    public void linesFromCallbacksOfTheTasksFuturesAttachToTheTask() throws Exception {
        JobDefinition job = JobDefinition.create("timed")
                .name("timed").version("1")
                .task(Tasks.fromCallable("wait", new Callable<Future<String>>() {

                    @Autowired
                    private TaskLogger logger;

                    @Override
                    public Future<String> call() {
                        return vertx.timer(10)
                                    .map(v -> "ticked")
                                    .onSuccess(value -> logger.info("timer {}", value));
                    }
                }));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertEquals(List.of("timer ticked"), linesOf(result.events()).stream().map(line -> line.entry().getMessage()).toList());
        assertEquals(List.of("timer ticked"),
                     repository.taskAt(handle.getJobRunId(), "0/1").getLogs().stream().map(TaskLogEntry::getMessage).toList());
    }

    @Test
    public void aFailedTaskKeepsWhatItWrote() throws Exception {
        JobDefinition job = JobDefinition.create("broken")
                .name("broken").version("1")
                .task(Tasks.fromCallable("delete batch", new Callable<Void>() {

                    @Autowired
                    private TaskLogger logger;

                    @Override
                    public Void call() {
                        logger.info("Deleting 2 workloads");
                        logger.error("Delete refused", new IllegalStateException("still open"));
                        throw new IllegalStateException("still open");
                    }
                }));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNotNull(result.error());
        TaskRecord record = repository.taskAt(handle.getJobRunId(), "0/1");
        assertEquals(ExecutionStatus.FAILED, record.getStatus());
        assertEquals(List.of("Deleting 2 workloads", "Delete refused: java.lang.IllegalStateException: still open"),
                     record.getLogs().stream().map(TaskLogEntry::getMessage).toList());
    }

    @Test
    public void theRecordKeepsTheMostRecentLines() throws Exception {
        int written = TaskLogger.MAX_RECORDED_ENTRIES + 5;
        JobDefinition job = JobDefinition.create("chatty")
                .name("chatty").version("1")
                .task(Tasks.fromCallable("chatter", new Callable<Void>() {

                    @Autowired
                    private TaskLogger logger;

                    @Override
                    public Void call() {
                        for (int i = 0; i < written; i++) {
                            logger.info("line {}", i);
                        }
                        return null;
                    }
                }));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertEquals(written, linesOf(result.events()).size());
        List<TaskLogEntry> kept = repository.taskAt(handle.getJobRunId(), "0/1").getLogs();
        assertEquals(TaskLogger.MAX_RECORDED_ENTRIES, kept.size());
        assertEquals("line 5", kept.getFirst().getMessage());
        assertEquals("line " + (written - 1), kept.getLast().getMessage());
    }

    @Test
    public void parallelAndDynamicTasksWriteUnderTheirOwnPaths() throws Exception {
        JobDefinition job = JobDefinition.create("fan out")
                .name("fan-out").version("1")
                .task(Tasks.fromCallable("plan", new Callable<JobDefinition>() {

                    @Autowired
                    private TaskLogger logger;

                    @Override
                    public JobDefinition call() {
                        logger.info("planned two nodes");
                        return JobDefinition.create("per node", JobScope.CHILD, true)
                                            .task(loggingTask("node-a"))
                                            .task(loggingTask("node-b"));
                    }
                }));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        List<TaskLogEvent> lines = linesOf(result.events());
        assertEquals(3, lines.size());
        assertTrue(lines.contains(lineAt(lines, "0/1", "planned two nodes")));
        assertTrue(lines.contains(lineAt(lines, "0/1/1/1", "checked node-a")));
        assertTrue(lines.contains(lineAt(lines, "0/1/1/2", "checked node-b")));
    }

    private Task<Void> loggingTask(String node) {
        return Tasks.fromCallable("check " + node, new Callable<>() {

            @Autowired
            private TaskLogger logger;

            @Override
            public Void call() {
                logger.info("checked {}", node);
                return null;
            }
        });
    }

    // The line written at the given path with the given message; a missing one fails the contains check
    private TaskLogEvent lineAt(List<TaskLogEvent> lines, String taskPath, String message) {
        return lines.stream()
                    .filter(line -> line.taskPath().equals(taskPath) && line.entry().getMessage().equals(message))
                    .findFirst()
                    .orElse(null);
    }

    private List<TaskLogEvent> linesOf(List<JobRunEvent> events) {
        return events.stream().filter(TaskLogEvent.class::isInstance).map(TaskLogEvent.class::cast).toList();
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
