package org.kinotic.test.tests.system;

import io.vertx.core.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.system.api.services.WatchEventService;
import org.kinotic.system.api.services.workload.VmNodeOrchestrationService;
import org.kinotic.system.internal.api.repositories.VmNodeRepository;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.support.system.NodeFixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The platform's ledger as the console's Change log page reads it: every record's entries in one
 * listing, newest first.
 */
@SpringBootTest
public class WatchEventServiceTests extends KinoticTestBase {

    private final String firstNode = "change-log-node-" + UUID.randomUUID();
    private final String secondNode = "change-log-node-" + UUID.randomUUID();

    @Autowired
    private WatchEventService watchEvents;

    @Autowired
    private VmNodeOrchestrationService nodeOrchestration;

    @Autowired
    private VmNodeRepository nodes;

    @AfterEach
    public void removeNodes() throws Exception {
        for (String nodeId : List.of(firstNode, secondNode)) {
            if (await(nodes.findById(nodeId)) != null) {
                await(nodes.deleteByIdSync(nodeId));
            }
        }
    }

    @Test
    public void listsEveryRecordsEntriesNewestFirst() throws Exception {
        await(runAsOrganization(() -> nodeOrchestration.registerNode(NodeFixtures.registration(firstNode, 4, 4096, 10240))));
        await(runAsOrganization(() -> nodeOrchestration.registerNode(NodeFixtures.registration(secondNode, 4, 4096, 10240))));

        // the ledger is visible to search after its next refresh
        long deadline = System.currentTimeMillis() + 30_000;
        List<WatchEvent> entries = latest();
        while (!namesBoth(entries) && System.currentTimeMillis() < deadline) {
            Thread.sleep(250);
            entries = latest();
        }

        assertTrue(namesBoth(entries), "the listing lacks one of the nodes: " + entries);
        for (int i = 1; i < entries.size(); i++) {
            assertFalse(entries.get(i).timestamp().after(entries.get(i - 1).timestamp()),
                        "entry " + i + " is newer than the one before it: " + entries);
        }
    }

    private List<WatchEvent> latest() throws Exception {
        return await(watchEvents.findAll(Pageable.create(0, 100, null))).getContent();
    }

    private boolean namesBoth(List<WatchEvent> entries) {
        Set<String> ids = entries.stream().map(WatchEvent::id).collect(Collectors.toSet());
        return ids.contains(firstNode) && ids.contains(secondNode);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
