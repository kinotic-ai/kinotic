package org.kinotic.test.tests.system;

import io.vertx.core.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.StatusConditions;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mark a departed server node leaves on a job run, written the way JobRunLivenessWatcher writes
 * it, against the run's record in Elasticsearch: a run still executing takes it, and a run that
 * finished between the watcher's search and its write keeps its outcome.
 */
@SpringBootTest
public class JobRunLivenessTests extends KinoticTestBase {

    private static final String DEPARTED_NODE_ID = "departed-node";

    @Autowired
    private JobRunRepository jobRuns;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    public void removeRuns() throws Exception {
        for (String id : created) {
            await(jobRuns.deleteByIdSync(id));
        }
        created.clear();
    }

    @Test
    public void runStillExecutingOnADepartedNodeTakesTheMark() throws Exception {
        JobRun run = executing();

        assertTrue(await(jobRuns.setCondition(run.getId(), nodeLeft(), "cluster membership")));

        JobRun stored = await(jobRuns.findRun(run.getId()));
        assertEquals(ExecutionStatus.RUNNING, stored.getStatus());
        assertTrue(StatusConditions.has(stored.getState().getConditions(), StatusConditionType.SERVER_NODE_LEFT));
    }

    @Test
    public void markFromASearchTheOutcomeOvertookIsDeclined() throws Exception {
        JobRun run = executing();
        // the watcher's search found the run executing; the node came back and finished it before the write
        await(jobRuns.recordOutcome(run.getId(), ExecutionStatus.COMPLETED, null, new Date(), "test"));

        assertFalse(await(jobRuns.setCondition(run.getId(), nodeLeft(), "cluster membership")));

        JobRun stored = await(jobRuns.findRun(run.getId()));
        assertEquals(ExecutionStatus.COMPLETED, stored.getStatus());
        assertFalse(StatusConditions.has(stored.getState().getConditions(), StatusConditionType.SERVER_NODE_LEFT));
    }

    /** A run recorded the way a job's start records it, executing on a node that is not a member. */
    private JobRun executing() throws Exception {
        JobRun run = new JobRun().setId(UUID.randomUUID().toString())
                                 .setName("liveness-run")
                                 .setOrganizationId(TEST_ORG_ID)
                                 .setNodeId(DEPARTED_NODE_ID)
                                 .setStarted(new Date());
        created.add(run.getId());
        return await(jobRuns.saveRun(run));
    }

    private static StatusCondition nodeLeft() {
        return new StatusCondition(StatusConditionType.SERVER_NODE_LEFT,
                                   "Node " + DEPARTED_NODE_ID + " left the cluster while the run was live",
                                   new Date());
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
