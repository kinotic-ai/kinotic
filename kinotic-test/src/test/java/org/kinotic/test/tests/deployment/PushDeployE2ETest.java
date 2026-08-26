package org.kinotic.test.tests.deployment;

import io.vertx.core.Future;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.ProjectDeployment;
import org.kinotic.management.api.model.ProjectDeploymentStatusType;
import org.kinotic.management.api.model.ProjectRepoToken;
import org.kinotic.management.api.model.workload.VmNode;
import org.kinotic.management.api.model.workload.VmNodeStatusType;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.management.api.services.ProjectRepoTokenProvider;
import org.kinotic.management.internal.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.internal.api.repositories.ProjectRepository;
import org.kinotic.system.api.services.VmNodeService;
import org.kinotic.system.api.services.WorkloadService;
import org.kinotic.system.internal.api.services.ProjectDeployService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end push-to-deploy pipeline against a real vm-manager node: first deployment
 * provisions the checkout and runtime workload, and a redeploy reuses both.
 * <p>
 * The test runs only when {@code KINOTIC_E2E_DEPLOY_REPO_URL} is set, and needs an
 * operator-run node registered with this server:
 * <ol>
 *     <li>Start this test's server context (the test does this), then start a vm-manager
 *         on a CLOUD_HYPERVISOR-capable host pointed at this server's gateway, with
 *         {@code KINOTIC_WORKLOAD_DATA_DIR} set. The test waits for the node to register.</li>
 *     <li>{@code KINOTIC_E2E_DEPLOY_REPO_URL} — https clone URL of a Kinotic project
 *         repository to deploy (public, or set {@code KINOTIC_E2E_DEPLOY_TOKEN}).</li>
 *     <li>{@code KINOTIC_E2E_DEPLOY_REF} — branch or commit sha to deploy, default {@code main}.</li>
 *     <li>When the node is not on this machine, override
 *         {@code kinotic.systemApi.deployment.serverHost} (default {@code localhost} in the
 *         test profile) with an address the node's workloads can reach.</li>
 * </ol>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "KINOTIC_E2E_DEPLOY_REPO_URL", matches = ".+")
public class PushDeployE2ETest extends KinoticTestBase {

    private static final String PROJECT_ID = "e2e-push-deploy";
    private static final long NODE_WAIT_MS = TimeUnit.MINUTES.toMillis(3);
    private static final long DEPLOY_WAIT_MS = TimeUnit.MINUTES.toMillis(15);

    @Autowired
    private ProjectDeployService projectDeployService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectDeploymentRepository projectDeploymentRepository;
    @Autowired
    private VmNodeService vmNodeService;
    @Autowired
    private WorkloadService workloadService;

    /**
     * Issues the fetch token from the environment instead of a GitHub App installation,
     * so the pipeline under test is everything from the job onward.
     */
    @TestConfiguration
    static class EnvRepoTokenProvider {
        @Bean
        @Primary
        ProjectRepoTokenProvider e2eRepoTokenProvider() {
            return (organizationId, projectId) -> Future.succeededFuture(
                    new ProjectRepoToken(System.getenv().getOrDefault("KINOTIC_E2E_DEPLOY_TOKEN", ""),
                                         Instant.now().plusSeconds(3600),
                                         System.getenv("KINOTIC_E2E_DEPLOY_REPO_URL"),
                                         ref()));
        }
    }

    @Test
    @Order(1)
    public void firstDeploymentProvisionsCheckoutAndRuntime() throws Exception {
        awaitOnlineNode();

        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setOrganizationId(TEST_ORG_ID);
        project.setApplicationId(TEST_APP_ID);
        project.setName("e2e-push-deploy");
        await(projectRepository.save(project, TEST_ORG_ID), TimeUnit.SECONDS.toMillis(30));

        await(projectDeployService.deployProject(TEST_ORG_ID, PROJECT_ID, ref()), DEPLOY_WAIT_MS);

        ProjectDeployment deployment = await(projectDeploymentRepository.findById(PROJECT_ID, TEST_ORG_ID),
                                             TimeUnit.SECONDS.toMillis(30));
        assertEquals(ProjectDeploymentStatusType.RUNNING, deployment.getStatus().type());
        assertEquals(ref(), deployment.getCommitSha());
        assertNotNull(deployment.getNodeId());
        assertTrue(deployment.getHostDir().endsWith("/projects/" + PROJECT_ID));
        assertNotNull(deployment.getRuntimeWorkloadId());

        Workload runtime = await(workloadService.findById(deployment.getRuntimeWorkloadId()),
                                 TimeUnit.SECONDS.toMillis(30));
        assertEquals(WorkloadStatus.RUNNING, runtime.getStatus());
        assertEquals(deployment.getNodeId(), runtime.getNodeId());
    }

    @Test
    @Order(2)
    public void redeployReusesTheNodeAndRuntimeWorkload() throws Exception {
        ProjectDeployment before = await(projectDeploymentRepository.findById(PROJECT_ID, TEST_ORG_ID),
                                         TimeUnit.SECONDS.toMillis(30));

        await(projectDeployService.deployProject(TEST_ORG_ID, PROJECT_ID, ref()), DEPLOY_WAIT_MS);

        ProjectDeployment after = await(projectDeploymentRepository.findById(PROJECT_ID, TEST_ORG_ID),
                                        TimeUnit.SECONDS.toMillis(30));
        assertEquals(ProjectDeploymentStatusType.RUNNING, after.getStatus().type());
        assertEquals(before.getNodeId(), after.getNodeId());
        assertEquals(before.getRuntimeWorkloadId(), after.getRuntimeWorkloadId());

        // the clean sync workload was destroyed, so only the runtime workload remains
        Page<Workload> onNode = await(workloadService.findAllForNode(after.getNodeId(), Pageable.ofSize(50)),
                                      TimeUnit.SECONDS.toMillis(30));
        assertTrue(onNode.getContent().stream()
                         .noneMatch(w -> w.getName().startsWith("project-sync-" + PROJECT_ID)));
    }

    private static String ref() {
        return System.getenv().getOrDefault("KINOTIC_E2E_DEPLOY_REF", "main");
    }

    private void awaitOnlineNode() throws Exception {
        awaitUntil(() -> {
            Page<VmNode> nodes = await(vmNodeService.findAll(Pageable.ofSize(10)), TimeUnit.SECONDS.toMillis(30));
            return nodes.getContent().stream()
                        .anyMatch(node -> node.getStatus().getType() == VmNodeStatusType.ONLINE
                                && node.getWorkloadDataDir() != null);
        }, NODE_WAIT_MS, "no ONLINE node with a workload data directory registered");
    }

    private <T> T await(Future<T> future, long timeoutMs) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(timeoutMs, TimeUnit.MILLISECONDS);
    }

    private static void awaitUntil(ThrowingCondition condition, long timeoutMs, String description) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.check()) {
            assertTrue(System.currentTimeMillis() < deadline, description);
            Thread.sleep(2_000);
        }
    }

    private interface ThrowingCondition {
        boolean check() throws Exception;
    }

}
