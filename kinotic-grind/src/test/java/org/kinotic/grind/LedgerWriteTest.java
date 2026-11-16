package org.kinotic.grind;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.ServerInfo;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.StatusConditions;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.grind.internal.api.services.DefaultJobService;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The run's record under failing and competing writes: a run whose record cannot be written is not
 * started, and its record is ended when the write landed though it failed; an outcome that cannot be
 * written is written again until it lands, and a run ended as abandoned while it executed keeps that
 * end.
 */
public class LedgerWriteTest extends AbstractGrindTest {

    private static final String STORE_DOWN = "store is down";

    @Test
    public void aRunWhoseRecordCannotBeWrittenIsNotStarted() throws Exception {
        AtomicInteger outcomeWrites = new AtomicInteger();
        InMemoryJobRunRepository unwritable = new InMemoryJobRunRepository() {
            @Override
            public synchronized Future<JobRun> saveRun(JobRun jobRun) {
                return Future.failedFuture(new IllegalStateException(STORE_DOWN));
            }

            @Override
            public synchronized Future<Boolean> recordOutcome(String jobRunId, ExecutionStatus status, String error, Date finished, String source) {
                outcomeWrites.incrementAndGet();
                return Future.succeededFuture(false);
            }
        };
        AtomicBoolean executed = new AtomicBoolean();
        JobDefinition job = JobDefinition.create("unrecorded")
                                         .name("unrecorded").version("1")
                                         .task(Tasks.fromRunnable("work", () -> executed.set(true)));

        RunResult result = await(jobServiceOn(unwritable).run(job, JobOwner.system()));

        assertInstanceOf(IllegalStateException.class, result.error());
        assertEquals(STORE_DOWN, result.error().getCause().getMessage());
        assertFalse(executed.get());
        assertTrue(unwritable.savedTasks.isEmpty());
        // no record landed, so there is none to end
        assertEquals(0, outcomeWrites.get());
    }

    @Test
    public void aRecordThatLandedThoughItsWriteFailedIsEnded() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        // a store that applies the write of the run, fails to acknowledge it, and cannot be read at first
        InMemoryJobRunRepository unacknowledged = new InMemoryJobRunRepository() {
            @Override
            public synchronized Future<JobRun> saveRun(JobRun jobRun) {
                return super.saveRun(jobRun).compose(saved -> Future.failedFuture(new IllegalStateException(STORE_DOWN)));
            }

            @Override
            public synchronized Future<JobRun> findRun(String jobRunId) {
                Future<JobRun> ret;
                if (reads.incrementAndGet() == 1) {
                    ret = Future.failedFuture(new IllegalStateException(STORE_DOWN));
                } else {
                    ret = super.findRun(jobRunId);
                }
                return ret;
            }
        };
        AtomicBoolean executed = new AtomicBoolean();
        JobDefinition job = JobDefinition.create("unacknowledged")
                                         .name("unacknowledged").version("1")
                                         .task(Tasks.fromRunnable("work", () -> executed.set(true)));

        JobRunHandle handle = jobServiceOn(unacknowledged).run(job, JobOwner.system());
        RunResult result = await(handle);

        assertInstanceOf(IllegalStateException.class, result.error());
        assertFalse(executed.get());
        JobRun run = unacknowledged.savedRuns.get(handle.getJobRunId());
        assertEquals(ExecutionStatus.FAILED, run.getStatus());
        assertTrue(run.getError().contains(STORE_DOWN));
        assertEquals(2, reads.get());
    }

    @Test
    public void anOutcomeThatCannotBeWrittenIsWrittenAgainUntilItLands() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        InMemoryJobRunRepository flaky = new InMemoryJobRunRepository() {
            @Override
            public synchronized Future<Boolean> recordOutcome(String jobRunId, ExecutionStatus status, String error, Date finished, String source) {
                Future<Boolean> ret;
                if (attempts.incrementAndGet() <= 3) {
                    ret = Future.failedFuture(new IllegalStateException(STORE_DOWN));
                } else {
                    ret = super.recordOutcome(jobRunId, status, error, finished, source);
                }
                return ret;
            }
        };
        JobDefinition job = JobDefinition.create("flaky outcome")
                                         .name("flaky-outcome").version("1")
                                         .task(Tasks.fromRunnable("work", () -> { }));

        JobRunHandle handle = jobServiceOn(flaky).run(job, JobOwner.system());

        // the run waits for its first three attempts, then ends its stream with the write still owed
        assertNull(await(handle).error());
        assertEquals(ExecutionStatus.RUNNING, flaky.savedRuns.get(handle.getJobRunId()).getStatus());

        awaitUntil("the outcome to be recorded",
                   () -> flaky.savedRuns.get(handle.getJobRunId()).getStatus() == ExecutionStatus.COMPLETED);
        assertEquals(4, attempts.get());
    }

    @Test
    public void aRunEndedAsAbandonedKeepsThatEnd() throws Exception {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        JobDefinition job = JobDefinition.create("partitioned")
                                         .name("partitioned").version("1")
                                         .task(Tasks.fromCallable("hold", () -> gate));
        JobRunHandle handle = jobService.run(job, JobOwner.system());
        handle.getEvents().subscribe(event -> { }, error -> { });
        awaitUntil("the run's task to start", () -> {
            var record = repository.taskAt(handle.getJobRunId(), "0/1");
            return record != null && record.getStatus() == ExecutionStatus.RUNNING;
        });
        StatusCondition left = new StatusCondition(StatusConditionType.SERVER_NODE_LEFT,
                                                   "Node " + TEST_NODE_ID + " left the cluster while the run was live",
                                                   new Date());

        // what another node writes when this one drops out of the cluster while it is still executing
        assertTrue(repository.recordAbandoned(handle.getJobRunId(), left, "cluster membership")
                             .toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS));
        gate.complete(null);
        assertNull(await(handle).error());

        JobRun run = repository.savedRuns.get(handle.getJobRunId());
        assertEquals(ExecutionStatus.FAILED, run.getStatus());
        assertEquals(left.message(), run.getError());
        assertEquals(left.since(), run.getFinished());
        assertTrue(StatusConditions.has(run.getState().getConditions(), StatusConditionType.SERVER_NODE_LEFT));
    }

    private DefaultJobService jobServiceOn(InMemoryJobRunRepository ledger) {
        DefaultJobService ret = new DefaultJobService(ledger, new ObjectMapper(), vertx,
                                                      () -> new ServerInfo(TEST_NODE_ID, "grind test node"), List.of());
        ret.setApplicationContext(appCtx);
        return ret;
    }

}
