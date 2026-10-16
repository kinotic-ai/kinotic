package org.kinotic.grind;

import org.junit.jupiter.api.Test;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.Task;
import org.kinotic.grind.api.model.TaskReporter;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.api.model.TaskViewKind;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.grind.api.model.events.JobRunEvent;
import org.kinotic.grind.api.model.events.TaskCompletedEvent;
import org.kinotic.grind.api.model.events.TaskProgressEvent;
import org.kinotic.grind.api.model.events.TaskStartedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task views: a task declares the view it shows from its start and on completion, and replaces it
 * mid-run through the {@link TaskReporter}; each view reaches watchers on the task's own events,
 * and the task's record keeps the latest one.
 */
public class TaskViewTest extends AbstractGrindTest {

    private static final TaskViewKind<Widget> WIDGET = new TaskViewKind<>("widget");
    private static final TaskViewKind<WidgetState> WIDGET_STATE = new TaskViewKind<>("widgetState");

    @Test
    public void aStartViewIsBuiltFromTheScopeAndShownWhenTheTaskStarts() throws Exception {
        JobDefinition job = JobDefinition.create("start view")
                .name("start-view").version("1")
                .input(new Widget("target"))
                .task(Tasks.noop("follow").startView(Widget.class, WIDGET::of));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        TaskStartedEvent started = startOf(result, "0/1");
        assertWidgetView(started.view(), "widget", "target");
        assertWidgetView(recordAt(handle, "0/1").getView(), "widget", "target");
    }

    @Test
    public void aResultViewIsBuiltFromTheTasksResult() throws Exception {
        JobDefinition job = JobDefinition.create("result view")
                .name("result-view").version("1")
                .task(Tasks.fromValue("produce", new Widget("made")).resultView(WIDGET::of));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertNull(startOf(result, "0/1").view());
        assertWidgetView(lastViewReportedBeforeCompletion(result, "0/1"), "widget", "made");
        assertWidgetView(recordAt(handle, "0/1").getView(), "widget", "made");
    }

    @Test
    public void aResultViewOfAnAsynchronousTaskIsBuiltFromTheValueItCompletesWith() throws Exception {
        JobDefinition job = JobDefinition.create("async result view")
                .name("async-result-view").version("1")
                .task(Tasks.fromCallable("produce later", () -> vertx.timer(10).map(v -> new Widget("later")))
                           .resultView(WIDGET::of));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertWidgetView(recordAt(handle, "0/1").getView(), "widget", "later");
    }

    @Test
    public void aResultViewLeavesTheTasksResultAsItWas() throws Exception {
        JobDefinition job = JobDefinition.create("result kept")
                .name("result-kept").version("1")
                .task(Tasks.fromValue("produce", new Widget("made"))
                           .store(Store.result("widget").wire())
                           .resultView(WIDGET::of));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        TaskCompletedEvent completed = completionOf(result, "0/1");
        assertEquals(new Widget("made"), completed.storedValue());
        assertEquals("made", completed.wireValue().get("name").stringValue());
    }

    @Test
    public void aResultViewCanAlsoReadAScopeValue() throws Exception {
        JobDefinition job = JobDefinition.create("result view with source")
                .name("result-view-source").version("1")
                .input(new Widget("target"))
                .task(Tasks.fromValue("decide", new WidgetState("decided"))
                           .resultView(Widget.class, (WidgetState decision, Widget target) -> WIDGET_STATE.of(
                                   new WidgetState(decision.name() + " for " + target.name()))));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertWidgetView(lastViewReportedBeforeCompletion(result, "0/1"), "widgetState", "decided for target");
    }

    @Test
    public void theReporterReplacesTheViewMidRunAndTheLatestIsRecorded() throws Exception {
        JobDefinition job = JobDefinition.create("changing view")
                .name("changing-view").version("1")
                .input(new Widget("first"))
                .task(Tasks.fromCallable("work", new Callable<Widget>() {

                    @Autowired
                    private TaskReporter reporter;

                    @Override
                    public Widget call() {
                        reporter.view(WIDGET.of(new Widget("second")));
                        return new Widget("third");
                    }
                }).startView(Widget.class, WIDGET::of).resultView(WIDGET::of));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        List<TaskProgressEvent> reports = result.events().stream()
                                                .filter(TaskProgressEvent.class::isInstance)
                                                .map(TaskProgressEvent.class::cast)
                                                .toList();
        assertEquals(2, reports.size());
        assertNull(reports.getFirst().percentageComplete());
        assertWidgetView(reports.getFirst().view(), "widget", "second");
        assertWidgetView(reports.get(1).view(), "widget", "third");
        assertWidgetView(startOf(result, "0/1").view(), "widget", "first");
        assertWidgetView(recordAt(handle, "0/1").getView(), "widget", "third");
    }

    @Test
    public void aFailedTaskKeepsTheViewItWasShowing() throws Exception {
        JobDefinition job = JobDefinition.create("failing")
                .name("failing").version("1")
                .input(new Widget("log"))
                .task(Tasks.<Widget>fromCallable("break", () -> {
                    throw new IllegalStateException("broken");
                }).startView(Widget.class, WIDGET::of).resultView(WIDGET::of));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNotNull(result.error());
        TaskRecord record = recordAt(handle, "0/1");
        assertEquals(ExecutionStatus.FAILED, record.getStatus());
        assertWidgetView(record.getView(), "widget", "log");
    }

    @Test
    public void aViewWhoseSourceTheScopeDoesNotHoldFailsTheTask() throws Exception {
        JobDefinition job = JobDefinition.create("missing source")
                .name("missing-source").version("1")
                .task(Tasks.noop("follow").startView(Widget.class, WIDGET::of));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNotNull(result.error());
        TaskRecord record = recordAt(handle, "0/1");
        assertEquals(ExecutionStatus.FAILED, record.getStatus());
        assertNotNull(record.getStarted());
        assertTrue(record.getError().contains("Widget"));
    }

    @Test
    public void aReplayedStateTaskBuildsItsDeclaredViewFromTheReplayedValue() throws Exception {
        AtomicBoolean fail = new AtomicBoolean(true);
        JobRunHandle original = jobService.run(replayJob(fail), JobOwner.system());
        assertNotNull(await(original).error());

        fail.set(false);
        JobRunHandle resumed = jobService.resume(original.getJobRunId(), replayJob(fail));
        RunResult result = await(resumed);

        assertNull(result.error());
        assertWidgetView(recordAt(resumed, "0/1").getView(), "widgetState", "decided");
    }

    @Test
    public void aSkippedTaskKeepsTheViewTheOriginalRunLeftIt() throws Exception {
        AtomicBoolean fail = new AtomicBoolean(true);
        JobRunHandle original = jobService.run(skippedJob(fail), JobOwner.system());
        assertNotNull(await(original).error());

        fail.set(false);
        JobRunHandle resumed = jobService.resume(original.getJobRunId(), skippedJob(fail));
        RunResult result = await(resumed);

        assertNull(result.error());
        assertWidgetView(lastViewReportedBeforeCompletion(result, "0/1"), "widget", "reported");
        assertWidgetView(recordAt(resumed, "0/1").getView(), "widget", "reported");
    }

    private JobDefinition replayJob(AtomicBoolean fail) {
        return JobDefinition.create("replay view")
                .name("replay-view").version("1")
                .task(Tasks.fromValue("decide", new WidgetState("decided"))
                           .store(Store.state("widgetState"))
                           .resultView(WIDGET_STATE::of))
                .task(failGate(fail));
    }

    private JobDefinition skippedJob(AtomicBoolean fail) {
        return JobDefinition.create("skipped view")
                .name("skipped-view").version("1")
                .task(Tasks.fromCallable("report", new Callable<Void>() {

                    @Autowired
                    private TaskReporter reporter;

                    @Override
                    public Void call() {
                        reporter.view(WIDGET.of(new Widget("reported")));
                        return null;
                    }
                }))
                .task(failGate(fail));
    }

    private Task<Void> failGate(AtomicBoolean fail) {
        return Tasks.fromCallable("fail gate", () -> {
            if (fail.get()) {
                throw new IllegalStateException("first run fails here");
            }
            return null;
        });
    }

    private void assertWidgetView(JsonNode view, String kind, String name) {
        assertNotNull(view);
        assertEquals(kind, view.get("kind").stringValue());
        assertEquals(name, view.get("data").get("name").stringValue());
    }

    private TaskRecord recordAt(JobRunHandle handle, String taskPath) {
        return repository.taskAt(handle.getJobRunId(), taskPath);
    }

    private TaskStartedEvent startOf(RunResult result, String taskPath) {
        return result.events().stream()
                     .filter(event -> event instanceof TaskStartedEvent started && started.taskPath().equals(taskPath))
                     .map(TaskStartedEvent.class::cast)
                     .findFirst()
                     .orElseThrow();
    }

    // The view of the last report for the task, which must come before the task's completion
    private JsonNode lastViewReportedBeforeCompletion(RunResult result, String taskPath) {
        List<JobRunEvent> events = result.events();
        int completed = events.indexOf(completionOf(result, taskPath));
        JsonNode ret = null;
        for (int i = 0; i < completed; i++) {
            if (events.get(i) instanceof TaskProgressEvent report && report.taskPath().equals(taskPath) && report.view() != null) {
                ret = report.view();
            }
        }
        return ret;
    }

    private TaskCompletedEvent completionOf(RunResult result, String taskPath) {
        return result.events().stream()
                     .filter(event -> event instanceof TaskCompletedEvent completed && completed.taskPath().equals(taskPath))
                     .map(TaskCompletedEvent.class::cast)
                     .findFirst()
                     .orElseThrow();
    }

}
