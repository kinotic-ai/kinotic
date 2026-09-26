package org.kinotic.test.tests.system;

import io.vertx.core.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.StatusConditions;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.services.workload.VmNodeOrchestrationService;
import org.kinotic.system.api.model.workload.VmNodeRegistration;
import org.kinotic.system.internal.api.repositories.VmNodeRepository;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.support.system.NodeFixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reconcile master as it runs in this context, one cluster singleton: a record's write queues its
 * worker, and a worker that asks to be called again is. Nothing here calls a worker; the outcomes are
 * the master's.
 */
@SpringBootTest
public class ReconcileMasterTests extends KinoticTestBase {

    private static final String NODE_ID = "master-node";
    private static final long DEFAULT_HEARTBEAT_TIMEOUT_SECONDS = 90;

    @Autowired
    private VmNodeOrchestrationService nodeOrchestration;

    @Autowired
    private VmNodeRepository nodes;

    @Autowired
    private KinoticSystemApiProperties properties;

    @AfterEach
    public void removeNode() throws Exception {
        properties.getSystemApi().getVmNode().setHeartbeatTimeoutSeconds(DEFAULT_HEARTBEAT_TIMEOUT_SECONDS);
        if (await(nodes.findById(NODE_ID)) != null) {
            await(nodes.deleteByIdSync(NODE_ID));
        }
    }

    @Test
    public void aNodeThatFallsSilentIsMarkedUnreachableByTheMaster() throws Exception {
        // the registration queues the node's worker, which asks to be called again once the next
        // heartbeat would be overdue; that call finds the node silent
        properties.getSystemApi().getVmNode().setHeartbeatTimeoutSeconds(1);
        await(runAsOrganization(() -> nodeOrchestration.registerNode(registration())));

        assertTrue(awaitUntil(() -> nodeUnreachable()), "the master never marked the silent node");
        assertFalse(await(nodes.findById(NODE_ID)).getState().isReconciled(), "nothing is placed on it");

        // by the one-second standard the node is silent again a second after any heartbeat, and the
        // master keeps looking; the default standard is back before the heartbeat that ends the silence
        properties.getSystemApi().getVmNode().setHeartbeatTimeoutSeconds(DEFAULT_HEARTBEAT_TIMEOUT_SECONDS);
        await(runAsOrganization(() -> nodeOrchestration.heartbeat(NODE_ID, List.of())));

        assertFalse(nodeUnreachable(), "the heartbeat ends the silence");
    }

    @Test
    public void aNodeWhoseDeregistrationWasAskedForIsFinalizedByTheMaster() throws Exception {
        await(runAsOrganization(() -> nodeOrchestration.registerNode(registration())));

        await(runAsOrganization(() -> nodeOrchestration.deregisterNode(NODE_ID)));

        assertTrue(awaitUntil(() -> await(nodes.findById(NODE_ID)) == null), "the master never finalized the deregistration");
        assertNull(await(nodes.findById(NODE_ID)));
    }

    private static VmNodeRegistration registration() {
        return NodeFixtures.registration(NODE_ID, 4, 4096, 10240);
    }

    private boolean nodeUnreachable() throws Exception {
        VmNode node = await(nodes.findById(NODE_ID));
        return node != null && StatusConditions.has(node.getState().getConditions(), StatusConditionType.NODE_UNREACHABLE);
    }

    // The master ticks every two seconds and the worker asks for its next call after the timeout
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
