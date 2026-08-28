package org.kinotic.grindv2;

import org.junit.jupiter.api.Test;
import org.kinotic.grindv2.api.model.JobDefinition;
import org.kinotic.grindv2.api.model.JobOwner;
import org.kinotic.grindv2.api.model.JobScope;
import org.kinotic.grindv2.api.model.ProgressReporter;
import org.kinotic.grindv2.api.model.events.JobRunEvent;
import org.kinotic.grindv2.api.model.events.TaskCompletedEvent;
import org.kinotic.grindv2.api.model.events.TaskProgressEvent;
import org.kinotic.grindv2.api.model.events.TaskStartedEvent;
import org.kinotic.grindv2.api.model.Tasks;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Progress reporting: a task injects the scope's {@link ProgressReporter}, and each report
 * reaches watchers as a {@link TaskProgressEvent} attributed to the reporting task.
 */
public class ProgressTest extends AbstractGrindV2Test {

    @Test
    public void reportsFlowBetweenTheTasksStartAndCompletion() throws Exception {
        JobDefinition job = JobDefinition.create("measured")
                .name("measured").version("1")
                .task(Tasks.fromCallable("pull image", new Callable<Void>() {

                    @Autowired
                    private ProgressReporter progress;

                    @Override
                    public Void call() {
                        progress.report(30, "downloading layers");
                        progress.report(80, "extracting");
                        return null;
                    }
                }));

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        List<JobRunEvent> events = result.events();
        assertEquals(List.of(new TaskProgressEvent("0/1", 30, "downloading layers"),
                             new TaskProgressEvent("0/1", 80, "extracting")),
                     events.stream().filter(TaskProgressEvent.class::isInstance).toList());
        int started = events.indexOf(new TaskStartedEvent("0/1", "pull image"));
        int firstReport = events.indexOf(new TaskProgressEvent("0/1", 30, "downloading layers"));
        int completed = indexOfCompletion(events, "0/1");
        assertTrue(started < firstReport && firstReport < completed);
    }

    @Test
    public void annotatedTaskInjectsTheReporterAsAParameter() throws Exception {
        JobDefinition job = JobDefinition.fromTasks(ProgressTasks.class).name("progress-tasks").version("1");

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        assertEquals(List.of(new TaskProgressEvent("0/1", 50, "halfway")),
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
        assertTrue(reports.contains(new TaskProgressEvent("0/1", 10, "left")));
        assertTrue(reports.contains(new TaskProgressEvent("0/2", 20, "right")));
    }

    private org.kinotic.grindv2.api.model.Task<Void> reportingTask(String message, int percentage) {
        return Tasks.fromCallable(message, new Callable<>() {

            @Autowired
            private ProgressReporter progress;

            @Override
            public Void call() {
                progress.report(percentage, message);
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
