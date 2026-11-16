package org.kinotic.test.tests.system;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Pageable;
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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The end of a run on a departed server node, written the way JobRunLivenessWatcher writes it,
 * against the run's record in Elasticsearch: a run still executing is ended FAILED with the
 * condition, a run that finished between the watcher's search and its write keeps its outcome, and
 * an outcome reported for a run already ended is declined.
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
    public void runStillExecutingOnADepartedNodeIsEndedAsFailed() throws Exception {
        JobRun run = executing();
        StatusCondition left = nodeLeft();

        assertTrue(await(jobRuns.recordAbandoned(run.getId(), left, "cluster membership")));

        JobRun stored = await(jobRuns.findRun(run.getId()));
        assertEquals(ExecutionStatus.FAILED, stored.getStatus());
        assertEquals(left.message(), stored.getError());
        assertEquals(left.since(), stored.getFinished());
        assertTrue(StatusConditions.has(stored.getState().getConditions(), StatusConditionType.SERVER_NODE_LEFT));
    }

    @Test
    public void endedRunIsNoLongerFoundExecutingElsewhere() throws Exception {
        JobRun run = executing();
        assertTrue(foundExecutingElsewhere(run));

        await(jobRuns.recordAbandoned(run.getId(), nodeLeft(), "cluster membership"));

        assertFalse(foundExecutingElsewhere(run));
    }

    @Test
    public void endFromASearchTheOutcomeOvertookIsDeclined() throws Exception {
        JobRun run = executing();
        // the watcher's search found the run executing; its node finished it before the write
        assertTrue(await(jobRuns.recordOutcome(run.getId(), ExecutionStatus.COMPLETED, null, new Date(), "test")));

        assertFalse(await(jobRuns.recordAbandoned(run.getId(), nodeLeft(), "cluster membership")));

        JobRun stored = await(jobRuns.findRun(run.getId()));
        assertEquals(ExecutionStatus.COMPLETED, stored.getStatus());
        assertNull(stored.getError());
        assertFalse(StatusConditions.has(stored.getState().getConditions(), StatusConditionType.SERVER_NODE_LEFT));
    }

    @Test
    public void outcomeOfARunAlreadyEndedIsDeclined() throws Exception {
        JobRun run = executing();
        StatusCondition left = nodeLeft();
        await(jobRuns.recordAbandoned(run.getId(), left, "cluster membership"));

        // the node was cut off from the cluster, kept executing, and reports the run's end after it rejoins
        assertFalse(await(jobRuns.recordOutcome(run.getId(), ExecutionStatus.COMPLETED, null, new Date(), "test")));

        JobRun stored = await(jobRuns.findRun(run.getId()));
        assertEquals(ExecutionStatus.FAILED, stored.getStatus());
        assertEquals(left.message(), stored.getError());
        assertTrue(StatusConditions.has(stored.getState().getConditions(), StatusConditionType.SERVER_NODE_LEFT));
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

    private boolean foundExecutingElsewhere(JobRun run) throws Exception {
        return await(jobRuns.findRunningElsewhere(Set.of("member-node"), Pageable.ofSize(200)))
                .getContent().stream()
                .anyMatch(found -> found.getId().equals(run.getId()));
    }

    private static StatusCondition nodeLeft() {
        return new StatusCondition(StatusConditionType.SERVER_NODE_LEFT,
                                   "Node " + DEPARTED_NODE_ID + " left the cluster while the run was live",
                                   new Date());
    }
}
