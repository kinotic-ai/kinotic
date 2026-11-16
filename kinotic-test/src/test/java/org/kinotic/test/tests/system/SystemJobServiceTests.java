package org.kinotic.test.tests.system;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.grind.api.repositories.TaskRecordRepository;
import org.kinotic.system.api.model.SystemJobDescriptor;
import org.kinotic.system.api.services.SystemJobService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The system jobs as the console's Jobs page drives them: listed, and started on behalf of
 * the system as recorded job runs. The job manager runs in this context as one cluster singleton, so
 * the scheduled jobs are also started by it.
 */
@SpringBootTest
public class SystemJobServiceTests extends KinoticTestBase {

    private static final String RETENTION_SWEEP = "workload-retention-sweep";
    private static final String JOB_RUN_RETENTION_SWEEP = "job-run-retention-sweep";

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

        assertEquals("2", sweep.getVersion());
        assertNotNull(sweep.getDescription());
    }

    @Test
    public void aStartedJobRunsForTheSystem() throws Exception {
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
    }

    @Test
    public void aScheduledJobIsStartedByTheJobManager() throws Exception {
        // every scheduled job with no run on record is due when the manager starts with this context
        long deadline = System.currentTimeMillis() + 30_000;
        JobRun latest = await(jobRuns.findLatestSystemRun(JOB_RUN_RETENTION_SWEEP));
        while (latest == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(250);
            latest = await(jobRuns.findLatestSystemRun(JOB_RUN_RETENTION_SWEEP));
        }

        assertNotNull(latest, "the manager never started the job run retention sweep");
        assertNull(latest.getOrganizationId());
    }

    @Test
    public void theJobRunRetentionSweepDeletesRunsThatEndedLongAgoWithTheirTaskRecords() throws Exception {
        Date longAgo = new Date(System.currentTimeMillis() - Duration.ofDays(31).toMillis());
        JobRun expired = saved(new JobRun().setStatus(ExecutionStatus.COMPLETED).setStarted(longAgo).setFinished(longAgo));
        await(taskRecords.saveSync(new TaskRecord().setId(expired.getId() + ":0/1")
                                                   .setJobRunId(expired.getId())
                                                   .setTaskPath("0/1")
                                                   .setStatus(ExecutionStatus.COMPLETED)));
        JobRun stillExecuting = saved(new JobRun().setStarted(longAgo));
        JobRun recent = saved(new JobRun().setStatus(ExecutionStatus.COMPLETED).setStarted(new Date()).setFinished(new Date()));

        JobRun sweep = await(systemJobs.startSystemJob(JOB_RUN_RETENTION_SWEEP));
        started.add(sweep.getId());
        assertEquals(ExecutionStatus.COMPLETED, awaitFinished(sweep.getId()).getStatus());

        assertNull(await(jobRuns.findRun(expired.getId())), "a run that ended over 30 days ago is deleted");
        assertTrue(await(jobRuns.findTasks(expired.getId())).isEmpty(), "its task records go with it");
        assertNotNull(await(jobRuns.findRun(stillExecuting.getId())), "a run still executing is kept however old");
        assertNotNull(await(jobRuns.findRun(recent.getId())), "a run that ended recently is kept");
    }

    @Test
    public void anUnknownJobIsRefused() {
        ExecutionException error = assertThrows(ExecutionException.class, () -> await(systemJobs.startSystemJob("no-such-job")));

        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    /** Saves a run of the retention test, visible to search, and removes it after the test. */
    private JobRun saved(JobRun run) throws Exception {
        run.setId(UUID.randomUUID().toString()).setName("retention-test").setNodeId("retention-test-node");
        started.add(run.getId());
        return await(jobRuns.saveSync(run));
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
}
