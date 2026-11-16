package org.kinotic.grind;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.ServerInfo;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.StatusConditions;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.grind.api.services.JobService;
import org.kinotic.grind.internal.api.services.DefaultJobService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.Date;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A node stopping while it executes a run: by the time the job service is destroyed the run is
 * recorded FAILED with SERVER_NODE_LEFT, its in-flight task is cancelled, the CANCELLED outcome the
 * cancelled run reports is declined, and its subscribers are told it was cancelled.
 */
public class NodeStopTest extends AbstractGrindTest {

    @Test
    public void aStoppingNodeEndsTheRunItIsExecuting() throws Exception {
        AtomicInteger outcomesReported = new AtomicInteger();
        InMemoryJobRunRepository ledger = new InMemoryJobRunRepository() {
            @Override
            public synchronized Future<Boolean> recordOutcome(String jobRunId, ExecutionStatus status, String error, Date finished, String source) {
                return super.recordOutcome(jobRunId, status, error, finished, source)
                            .onComplete(recorded -> outcomesReported.incrementAndGet());
            }
        };
        AtomicReference<Throwable> streamError = new AtomicReference<>();
        JobRunHandle handle;

        // the job service as the node's context holds it, so closing the context is the node stopping
        try (AnnotationConfigApplicationContext nodeCtx = new AnnotationConfigApplicationContext()) {
            nodeCtx.registerBean(DefaultJobService.class,
                                 () -> new DefaultJobService(ledger, new ObjectMapper(), vertx,
                                                             () -> new ServerInfo(TEST_NODE_ID, "grind test node")));
            nodeCtx.refresh();
            JobDefinition job = JobDefinition.create("interrupted by a stop")
                                             .name("interrupted").version("1")
                                             .task(Tasks.fromCallable("hold", CompletableFuture::new));
            handle = nodeCtx.getBean(JobService.class).run(job, JobOwner.system());
            handle.getEvents().subscribe(event -> { }, streamError::set);
            String jobRunId = handle.getJobRunId();
            awaitUntil("the run's task to start", () -> {
                var record = ledger.taskAt(jobRunId, "0/1");
                return record != null && record.getStatus() == ExecutionStatus.RUNNING;
            });
        }

        JobRun run = ledger.savedRuns.get(handle.getJobRunId());
        assertEquals(ExecutionStatus.FAILED, run.getStatus());
        assertEquals("Node " + TEST_NODE_ID + " stopped while the run was live", run.getError());
        assertTrue(StatusConditions.has(run.getState().getConditions(), StatusConditionType.SERVER_NODE_LEFT));

        awaitUntil("the cancelled run to report its outcome", () -> outcomesReported.get() == 1);
        assertEquals(ExecutionStatus.CANCELLED, ledger.taskAt(handle.getJobRunId(), "0/1").getStatus());
        assertEquals(ExecutionStatus.FAILED, run.getStatus());
        // what a subscriber tells a run its node cut short from one that failed
        assertInstanceOf(CancellationException.class, streamError.get());
    }

}
