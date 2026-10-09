package org.kinotic.test.tests.system;

import io.vertx.core.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.TaskLogEntry;
import org.kinotic.grind.api.model.TaskLogLevel;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.grind.api.repositories.TaskRecordRepository;
import org.kinotic.system.api.model.SystemJobDescriptor;
import org.kinotic.system.api.services.SystemJobService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The system jobs as the console's System jobs page drives them: listed, started on behalf of the
 * platform, and recorded with the lines each task wrote to its log.
 */
@SpringBootTest
public class SystemJobServiceTests extends KinoticTestBase {

    private static final String RETENTION_SWEEP = "workload-retention-sweep";

    @Autowired
    private SystemJobService systemJobs;

    @Autowired
    private JobRunRepository jobRuns;

    @Autowired
    private TaskRecordRepository taskRecords;

    private final List<String> started = new ArrayList<>();

    @AfterEach
    public void removeRuns() throws Exception {
        for (String id : started) {
            for (TaskRecord record : await(jobRuns.findTasks(id))) {
                await(taskRecords.deleteByIdSync(record.getId()));
            }
            await(jobRuns.deleteByIdSync(id));
        }
        started.clear();
    }

    @Test
    public void listsTheRetentionSweep() throws Exception {
        SystemJobDescriptor sweep = await(systemJobs.findSystemJobs()).stream()
                                                                       .filter(job -> job.getName().equals(RETENTION_SWEEP))
                                                                       .findFirst()
                                                                       .orElseThrow();

        assertEquals("1", sweep.getVersion());
        assertNotNull(sweep.getDescription());
    }

    @Test
    public void aStartedJobIsRecordedForThePlatformWithItsTaskLog() throws Exception {
        JobRun run = await(systemJobs.startSystemJob(RETENTION_SWEEP));
        started.add(run.getId());

        assertEquals(RETENTION_SWEEP, run.getName());
        assertNull(run.getOrganizationId());

        JobRun finished = awaitFinished(run.getId());
        assertEquals(ExecutionStatus.COMPLETED, finished.getStatus());

        TaskRecord sweep = await(jobRuns.findTasks(run.getId())).stream()
                                                                .filter(record -> record.getTaskPath().equals("0/1"))
                                                                .findFirst()
                                                                .orElseThrow();
        assertEquals(ExecutionStatus.COMPLETED, sweep.getStatus());
        TaskLogEntry last = sweep.getLogs().getLast();
        assertEquals(TaskLogLevel.INFO, last.getLevel());
        assertTrue(last.getMessage().matches("Deleted \\d+ workloads in all whose runs ended more than \\d+ days ago"),
                   last.getMessage());
        assertNotNull(last.getTimestamp());
    }

    @Test
    public void anUnknownJobIsRefused() {
        ExecutionException error = assertThrows(ExecutionException.class, () -> await(systemJobs.startSystemJob("no-such-job")));

        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    private JobRun awaitFinished(String jobRunId) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        JobRun ret = await(jobRuns.findRun(jobRunId));
        while (ret.getStatus() == ExecutionStatus.RUNNING && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
            ret = await(jobRuns.findRun(jobRunId));
        }
        return ret;
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
