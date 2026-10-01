package org.kinotic.test.tests.system;

import io.vertx.core.Future;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.Kinotic;
import org.kinotic.system.api.model.cluster.KinoticClusterInfo;
import org.kinotic.system.api.model.cluster.KinoticNodeInfo;
import org.kinotic.system.api.services.KinoticClusterInfoService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

/**
 * Exercises the cluster view against a real Ignite node: what a node reports about itself travels as the node
 * attributes it joined with.
 */
@SpringBootTest
public class KinoticClusterInfoTests extends KinoticTestBase {

    @Autowired
    private KinoticClusterInfoService clusterInfoService;

    @Autowired
    private Kinotic kinotic;

    @Test
    public void theNodeReportsTheServerItRuns() throws Exception {
        KinoticClusterInfo clusterInfo = await(clusterInfoService.getClusterInfo());

        Assertions.assertEquals("kinotic-test", localNode(clusterInfo).getServerName());
    }

    @Test
    public void theNodeReportsTheLabelsOfItsLogs() throws Exception {
        KinoticClusterInfo clusterInfo = await(clusterInfoService.getClusterInfo());

        KinoticNodeInfo localNode = localNode(clusterInfo);
        Assertions.assertEquals("kinotic-test", localNode.getTelemetryServiceName());
        Assertions.assertEquals("kinotic-test-1", localNode.getTelemetryServiceInstanceId());
    }

    private KinoticNodeInfo localNode(KinoticClusterInfo clusterInfo) {
        String localNodeId = kinotic.serverInfo().getNodeId();
        return clusterInfo.getNodes()
                          .stream()
                          .filter(node -> node.getNodeId().equals(localNodeId))
                          .findFirst()
                          .orElseThrow();
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
