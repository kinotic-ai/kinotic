package org.kinotic.test.tests.system;

import io.vertx.core.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.DeploymentState;
import org.kinotic.domain.api.model.DeploymentStatusType;
import org.kinotic.domain.api.model.Requeue;
import org.kinotic.domain.api.services.Reconciler;
import org.kinotic.management.api.model.MicroserviceArtifact;
import org.kinotic.management.api.model.MicroserviceDeployment;
import org.kinotic.management.api.model.ProjectArtifacts;
import org.kinotic.management.api.model.ProjectDeployment;
import org.kinotic.management.api.model.UiDeployment;
import org.kinotic.management.api.repositories.MicroserviceDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deployment workers against their real repositories: what each answers for an intent it cannot
 * carry out, for a record the commit dropped, and for a removal. A worker is called directly where
 * the test asserts its answer, as the reconcile master calls it; a removal is left to the master
 * running in this context, and the test waits for the record to go.
 */
@SpringBootTest
public class DeployWorkerTests extends KinoticTestBase {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    @Autowired
    private Reconciler<MicroserviceDeployment> microserviceWorker;

    @Autowired
    private Reconciler<UiDeployment> uiWorker;

    @Autowired
    private Reconciler<ProjectDeployment> projectWorker;

    @Autowired
    private MicroserviceDeploymentRepository microservices;

    @Autowired
    private UiDeploymentRepository uis;

    @Autowired
    private ProjectDeploymentRepository projectDeployments;

    private final List<String> microserviceIds = new ArrayList<>();
    private final List<String> uiIds = new ArrayList<>();
    private final List<String> projectIds = new ArrayList<>();

    @AfterEach
    public void removeCreatedRecords() throws Exception {
        for (String id : microserviceIds) {
            if (await(microservices.findById(id)) != null) {
                await(microservices.deleteByIdSync(id));
            }
        }
        for (String id : uiIds) {
            if (await(uis.findById(id)) != null) {
                await(uis.deleteByIdSync(id));
            }
        }
        for (String id : projectIds) {
            if (await(projectDeployments.findById(id, TEST_ORG_ID)) != null) {
                await(projectDeployments.deleteByIdSync(id, TEST_ORG_ID));
            }
        }
        microserviceIds.clear();
        uiIds.clear();
        projectIds.clear();
    }

    @Test
    public void aMicroserviceOfAProjectNeverDeployedFailsItsIntent() throws Exception {
        MicroserviceDeployment deployment = microservice("worker-no-target", "api", new DeploymentState(DeploymentStatusType.RUNNING, COMMIT));

        Requeue requeue = await(microserviceWorker.reconcile(deployment));

        assertEquals(Requeue.NONE, requeue, "the next push or a restart renews the intent");
        MicroserviceDeployment answered = await(microservices.findById(deployment.getId()));
        assertEquals(DeploymentStatusType.FAILED, answered.getState().getObserved().phase());
        assertEquals(answered.getState().getGeneration(), answered.getState().getObservedGeneration(), "the intent is answered");
        assertFalse(answered.getState().isReconciled());
        assertTrue(answered.getFailureMessage().contains("no deployment target"), answered.getFailureMessage());
    }

    @Test
    public void aMicroserviceAheadOfItsProjectsArtifactsWaitsForTheDeployJob() throws Exception {
        String projectId = "worker-artifacts";
        projectDeployment(projectId, new DeploymentState(DeploymentStatusType.RUNNING, "old"));
        await(projectDeployments.recordTarget(projectId, TEST_ORG_ID, "node-x", "/srv/" + projectId, null, null));
        await(projectDeployments.recordArtifacts(projectId, TEST_ORG_ID,
                                                 new ProjectArtifacts(List.of(new MicroserviceArtifact("api", "services/api", "index.ts")), List.of()),
                                                 "old"));
        MicroserviceDeployment deployment = microservice(projectId, "api", new DeploymentState(DeploymentStatusType.RUNNING, COMMIT));

        Requeue requeue = await(microserviceWorker.reconcile(deployment));

        assertTrue(requeue.wanted(), "the job records the commit's artifacts before it writes the intents");
        assertEquals(Duration.ofSeconds(10), requeue.after());
        MicroserviceDeployment waiting = await(microservices.findById(deployment.getId()));
        assertNull(waiting.getState().getObserved(), "nothing is answered while the artifacts are behind");
    }

    @Test
    public void anOrphanedMicroserviceAnswersTheIntentAndIsLeftAsItIs() throws Exception {
        DeploymentState orphaned = new DeploymentState(DeploymentStatusType.ORPHANED, COMMIT);
        MicroserviceDeployment deployment = microservice("worker-orphaned", "api", orphaned);

        Requeue requeue = await(microserviceWorker.reconcile(deployment));

        assertEquals(Requeue.NONE, requeue);
        MicroserviceDeployment answered = await(microservices.findById(deployment.getId()));
        assertEquals(orphaned, answered.getState().getObserved());
        assertTrue(answered.getState().isReconciled());
    }

    @Test
    public void aMicroserviceWhoseRemovalWasAskedForIsFinalizedByItsWorker() throws Exception {
        MicroserviceDeployment deployment = microservice("worker-removed", "api", new DeploymentState(DeploymentStatusType.ORPHANED, COMMIT));

        await(microservices.requestDeletion(deployment.getId(), "removeMicroservice"));

        awaitGone(() -> microservices.findById(deployment.getId()), "microservice deployment " + deployment.getId());
    }

    @Test
    public void anOrphanedUiAnswersTheIntentAndKeepsServing() throws Exception {
        DeploymentState orphaned = new DeploymentState(DeploymentStatusType.ORPHANED, COMMIT);
        UiDeployment deployment = ui("worker-ui-orphaned", "site", orphaned);

        Requeue requeue = await(uiWorker.reconcile(deployment));

        assertEquals(Requeue.NONE, requeue);
        UiDeployment answered = await(uis.findById(deployment.getId()));
        assertEquals(orphaned, answered.getState().getObserved());
        assertTrue(answered.getState().isReconciled());
    }

    @Test
    public void aUiWhoseRemovalWasAskedForIsFinalizedByItsWorker() throws Exception {
        // its project was never deployed, so there is no node to delete files through: the record goes on its own
        UiDeployment deployment = ui("worker-ui-removed", "site", new DeploymentState(DeploymentStatusType.ORPHANED, COMMIT));

        await(uis.requestDeletion(deployment.getId(), "removeUiSite"));

        awaitGone(() -> uis.findById(deployment.getId()), "UI deployment " + deployment.getId());
    }

    @Test
    public void aProjectDeploymentWithoutChildrenIsFinalizedByItsWorker() throws Exception {
        String projectId = "worker-project-removed";
        projectDeployment(projectId, new DeploymentState(DeploymentStatusType.RUNNING, COMMIT));

        await(projectDeployments.requestDeletion(projectId, TEST_ORG_ID, "deletion of project " + projectId));

        awaitGone(() -> projectDeployments.findById(projectId, TEST_ORG_ID), "project deployment " + projectId);
    }

    @Test
    public void aProjectDeploymentTakesItsChildrenWithIt() throws Exception {
        String projectId = "worker-project-children";
        projectDeployment(projectId, new DeploymentState(DeploymentStatusType.RUNNING, COMMIT));
        MicroserviceDeployment child = microservice(projectId, "api", new DeploymentState(DeploymentStatusType.ORPHANED, COMMIT));
        UiDeployment site = ui(projectId, "site", new DeploymentState(DeploymentStatusType.ORPHANED, COMMIT));

        await(projectDeployments.requestDeletion(projectId, TEST_ORG_ID, "deletion of project " + projectId));

        // bottom-up: the children's removal is asked for and their workers carry it out, then the record goes
        awaitGone(() -> microservices.findById(child.getId()), "microservice deployment " + child.getId());
        awaitGone(() -> uis.findById(site.getId()), "UI deployment " + site.getId());
        awaitGone(() -> projectDeployments.findById(projectId, TEST_ORG_ID), "project deployment " + projectId);
    }

    private MicroserviceDeployment microservice(String projectId, String name, DeploymentState desired) throws Exception {
        String id = projectId + "-" + name;
        MicroserviceDeployment upsert = new MicroserviceDeployment().setId(id)
                                                                    .setOrganizationId(TEST_ORG_ID)
                                                                    .setApplicationId(TEST_APP_ID)
                                                                    .setProjectId(projectId)
                                                                    .setName(name)
                                                                    .setCreated(new Date())
                                                                    .setUpdated(new Date());
        microserviceIds.add(id);
        MicroserviceDeployment deployment = await(microservices.updateDesired(id, desired, upsert, "push of " + desired.commitSha()));
        assertNotNull(deployment);
        return deployment;
    }

    private UiDeployment ui(String projectId, String name, DeploymentState desired) throws Exception {
        String id = projectId + "-" + name;
        UiDeployment upsert = new UiDeployment().setId(id)
                                                .setOrganizationId(TEST_ORG_ID)
                                                .setApplicationId(TEST_APP_ID)
                                                .setProjectId(projectId)
                                                .setName(name)
                                                .setCreated(new Date())
                                                .setUpdated(new Date());
        uiIds.add(id);
        UiDeployment deployment = await(uis.updateDesired(id, desired, upsert, "push of " + desired.commitSha()));
        assertNotNull(deployment);
        return deployment;
    }

    private ProjectDeployment projectDeployment(String projectId, DeploymentState desired) throws Exception {
        ProjectDeployment upsert = new ProjectDeployment().setId(projectId)
                                                          .setOrganizationId(TEST_ORG_ID)
                                                          .setApplicationId(TEST_APP_ID)
                                                          .setCreated(new Date())
                                                          .setUpdated(new Date());
        projectIds.add(projectId);
        ProjectDeployment deployment = await(projectDeployments.updateDesired(projectId, TEST_ORG_ID, desired, upsert, "push of " + desired.commitSha()));
        assertNotNull(deployment);
        return deployment;
    }

    /** Waits for the master to call the record's worker for its removal and the worker to delete it. */
    private static <T> void awaitGone(Supplier<Future<T>> read, String what) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (await(read.get()) != null && System.currentTimeMillis() < deadline) {
            Thread.sleep(250);
        }
        Assertions.assertNull(await(read.get()), what + " should have been finalized by its worker");
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
