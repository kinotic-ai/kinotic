package org.kinotic.test.tests.system;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import io.vertx.core.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.api.model.WatchEventKind;
import org.kinotic.domain.api.model.WatchedType;
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
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ledger of a watched record as the reconcile master running in this context keeps it: every
 * write that lands is entered, in the order the writes landed.
 */
@SpringBootTest
public class WatchEventLedgerTests extends KinoticTestBase {

    private static final String SOURCE = "ledger test";

    @Autowired
    private VmNodeOrchestrationService nodeOrchestration;

    @Autowired
    private VmNodeRepository nodes;

    @Autowired
    private WatchEventRepository watchEvents;

    @Autowired
    private ElasticsearchAsyncClient elasticsearch;

    // The ledger outlives a deleted record, so each test's node has an id of its own
    private String nodeId;

    @BeforeEach
    public void nameNode() {
        nodeId = "ledger-node-" + UUID.randomUUID();
    }

    @AfterEach
    public void removeNode() throws Exception {
        blockLedgerWrites(false);
        if (await(nodes.findById(nodeId)) != null) {
            await(nodes.deleteByIdSync(nodeId));
        }
    }

    @Test
    public void aRecordsEntriesAreStampedInTheOrderItsWritesLanded() throws Exception {
        await(runAsOrganization(() -> nodeOrchestration.registerNode(NodeFixtures.registration(nodeId, 4, 4096, 10240))));
        VmNode node = await(nodes.findById(nodeId));

        // the clear follows the set at once, as a heartbeat answering the mark does
        assertTrue(await(nodes.setCondition(node, unreachable(), SOURCE)));
        assertTrue(await(nodes.clearCondition(nodeId, StatusConditionType.NODE_UNREACHABLE, SOURCE)));

        assertTrue(awaitUntil(() -> history().size() == 4), "the writes never reached the ledger");
        List<WatchEvent> history = history();
        assertEquals(WatchEventKind.CONDITION_CLEARED, history.get(0).kind(), history.toString());
        assertEquals(WatchEventKind.CONDITION_SET, history.get(1).kind(), history.toString());
        for (int i = 1; i < history.size(); i++) {
            Date newer = history.get(i - 1).timestamp();
            Date older = history.get(i).timestamp();
            assertTrue(newer.after(older), "entry " + i + " is not older than the one before it: " + history);
        }
    }

    @Test
    public void aWriteTheLedgerRefusedIsEnteredByTheMasterOnceItAccepts() throws Exception {
        await(runAsOrganization(() -> nodeOrchestration.registerNode(NodeFixtures.registration(nodeId, 4, 4096, 10240))));
        VmNode node = await(nodes.findById(nodeId));

        blockLedgerWrites(true);
        assertTrue(await(nodes.setCondition(node, unreachable(), SOURCE)), "the write lands though the ledger refuses its entry");
        assertEquals(1, await(nodes.findById(nodeId)).getState().getUnrecorded().size(), "the record holds the entry");

        blockLedgerWrites(false);
        assertTrue(awaitUntil(this::conditionSetEntered), "the master never entered the held entry");
        assertTrue(awaitUntil(() -> await(nodes.findById(nodeId)).getState().getUnrecorded().isEmpty()),
                   "the record still holds the entry once it is entered");
        assertEquals(1, history().stream().filter(event -> event.kind() == WatchEventKind.CONDITION_SET).count(),
                     "the entry was entered once");
    }

    @Test
    public void aRecordHoldingAnEntryIsDeletedOnlyOnceTheEntryIsEntered() throws Exception {
        await(runAsOrganization(() -> nodeOrchestration.registerNode(NodeFixtures.registration(nodeId, 4, 4096, 10240))));
        VmNode node = await(nodes.findById(nodeId));

        blockLedgerWrites(true);
        assertTrue(await(nodes.setCondition(node, unreachable(), SOURCE)));
        assertThrows(ExecutionException.class, () -> await(nodes.deleteByIdSync(nodeId)),
                     "the delete would take the held entry with it");
        assertNotNull(await(nodes.findById(nodeId)), "the record holding the entry is kept");

        blockLedgerWrites(false);
        await(nodes.deleteByIdSync(nodeId));
        assertNull(await(nodes.findById(nodeId)));
        assertTrue(awaitUntil(() -> await(watchEvents.findHistory(WatchedType.VM_NODE, null, nodeId, Pageable.create(0, 50, null)))
                                           .getContent().stream()
                                           .anyMatch(event -> event.kind() == WatchEventKind.CONDITION_SET && SOURCE.equals(event.source()))),
                   "the held entry was not entered before the record was deleted");
    }

    private StatusCondition unreachable() {
        return new StatusCondition(StatusConditionType.NODE_UNREACHABLE, "Node " + nodeId + " missed its heartbeat", new Date());
    }

    private boolean conditionSetEntered() throws Exception {
        return history().stream().anyMatch(event -> event.kind() == WatchEventKind.CONDITION_SET && SOURCE.equals(event.source()));
    }

    private List<WatchEvent> history() throws Exception {
        VmNode node = await(nodes.findById(nodeId));
        return await(nodes.findHistory(node, Pageable.create(0, 50, null))).getContent();
    }

    private void blockLedgerWrites(boolean blocked) throws Exception {
        elasticsearch.indices()
                     .putSettings(r -> r.index(WatchEventRepository.DATA_STREAM)
                                        .settings(s -> s.blocks(b -> b.write(blocked))))
                     .get(30, TimeUnit.SECONDS);
    }

    // The ledger is visible to search after its next refresh, and the master ticks every two seconds

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
