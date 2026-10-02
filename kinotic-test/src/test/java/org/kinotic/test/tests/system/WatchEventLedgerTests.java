package org.kinotic.test.tests.system;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import io.vertx.core.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.api.model.WatchEventKind;
import org.kinotic.domain.internal.api.repositories.WatchEventRepository;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.services.workload.VmNodeOrchestrationService;
import org.kinotic.system.internal.api.repositories.VmNodeRepository;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.support.system.NodeFixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ledger of a watched record as the reconcile master running in this context keeps it: every
 * write that lands is entered, in the order the writes landed.
 */
@SpringBootTest
public class WatchEventLedgerTests extends KinoticTestBase {

    private static final String NODE_ID = "ledger-node";
    private static final String SOURCE = "ledger test";

    @Autowired
    private VmNodeOrchestrationService nodeOrchestration;

    @Autowired
    private VmNodeRepository nodes;

    @Autowired
    private ElasticsearchAsyncClient elasticsearch;

    @AfterEach
    public void removeNode() throws Exception {
        blockLedgerWrites(false);
        if (await(nodes.findById(NODE_ID)) != null) {
            await(nodes.deleteByIdSync(NODE_ID));
        }
    }

    @Test
    public void aRecordsEntriesAreStampedInTheOrderItsWritesLanded() throws Exception {
        await(runAsOrganization(() -> nodeOrchestration.registerNode(NodeFixtures.registration(NODE_ID, 4, 4096, 10240))));
        VmNode node = await(nodes.findById(NODE_ID));

        // the clear follows the set at once, as a heartbeat answering the mark does
        assertTrue(await(nodes.setCondition(node, unreachable(), SOURCE)));
        assertTrue(await(nodes.clearCondition(NODE_ID, StatusConditionType.NODE_UNREACHABLE, SOURCE)));

        assertTrue(awaitUntil(() -> history().size() >= 4), "the writes never reached the ledger");
        List<WatchEvent> history = history();
        assertEquals(WatchEventKind.CONDITION_CLEARED, history.get(0).kind());
        assertEquals(WatchEventKind.CONDITION_SET, history.get(1).kind());
        for (int i = 1; i < history.size(); i++) {
            Date newer = history.get(i - 1).timestamp();
            Date older = history.get(i).timestamp();
            assertTrue(newer.after(older), "entry " + i + " is not older than the one before it: " + history);
        }
    }

    @Test
    public void aWriteTheLedgerRefusedIsEnteredByTheMasterOnceItAccepts() throws Exception {
        await(runAsOrganization(() -> nodeOrchestration.registerNode(NodeFixtures.registration(NODE_ID, 4, 4096, 10240))));
        VmNode node = await(nodes.findById(NODE_ID));

        blockLedgerWrites(true);
        assertTrue(await(nodes.setCondition(node, unreachable(), SOURCE)), "the write lands though the ledger refuses its entry");
        assertEquals(1, await(nodes.findById(NODE_ID)).getState().getUnrecorded().size(), "the record holds the entry");

        blockLedgerWrites(false);
        assertTrue(awaitUntil(this::conditionSetEntered), "the master never entered the held entry");
        assertTrue(awaitUntil(() -> await(nodes.findById(NODE_ID)).getState().getUnrecorded().isEmpty()),
                   "the record still holds the entry once it is entered");
        assertEquals(1, history().stream().filter(event -> event.kind() == WatchEventKind.CONDITION_SET).count(),
                     "the entry was entered once");
    }

    private static StatusCondition unreachable() {
        return new StatusCondition(StatusConditionType.NODE_UNREACHABLE, "Node " + NODE_ID + " missed its heartbeat", new Date());
    }

    private boolean conditionSetEntered() throws Exception {
        return history().stream().anyMatch(event -> event.kind() == WatchEventKind.CONDITION_SET && SOURCE.equals(event.source()));
    }

    private List<WatchEvent> history() throws Exception {
        VmNode node = await(nodes.findById(NODE_ID));
        return await(nodes.findHistory(node, Pageable.create(0, 50, null))).getContent();
    }

    private void blockLedgerWrites(boolean blocked) throws Exception {
        elasticsearch.indices()
                     .putSettings(r -> r.index(WatchEventRepository.DATA_STREAM)
                                        .settings(s -> s.blocks(b -> b.write(blocked))))
                     .get(30, TimeUnit.SECONDS);
    }

    // The ledger is visible to search after its next refresh, and the master ticks every two seconds
    private static boolean awaitUntil(Check condition) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!condition.holds() && System.currentTimeMillis() < deadline) {
            Thread.sleep(250);
        }
        return condition.holds();
    }

    private interface Check {
        boolean holds() throws Exception;
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
