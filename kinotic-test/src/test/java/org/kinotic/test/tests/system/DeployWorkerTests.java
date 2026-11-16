package org.kinotic.test.tests.system;

import io.vertx.core.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.ServiceRegistry;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Requeue;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.WatchedParent;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.api.services.Reconciler;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.deployment.DeployTarget;
import org.kinotic.management.api.model.deployment.DeploymentState;
import org.kinotic.management.api.model.deployment.DeploymentStatusType;
import org.kinotic.management.api.model.deployment.MicroserviceArtifact;
import org.kinotic.management.api.model.deployment.MicroserviceDeployment;
import org.kinotic.management.api.model.deployment.ProjectArtifacts;
import org.kinotic.management.api.model.deployment.ProjectDependencies;
import org.kinotic.management.api.model.deployment.ProjectDeployment;
import org.kinotic.management.api.model.deployment.UiDeployment;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.management.api.repositories.MicroserviceDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectDependenciesRepository;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.management.api.repositories.WorkloadRepository;
import org.kinotic.system.api.services.deployment.DeploymentOperationsService;
import org.kinotic.system.api.services.workload.VmNodeOrchestrationService;
import org.kinotic.system.internal.api.repositories.VmNodeRepository;
import org.kinotic.system.internal.api.services.deployment.ProjectWorkloadSizes;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.support.system.NodeFixtures;
import org.kinotic.test.support.system.StubVmManager;
import org.kinotic.test.support.system.VmManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deployment workers against their real repositories: what each answers for an intent it cannot
 * carry out, for a record the commit dropped, and for a removal, what the microservice worker
 * does with a restart, and what the project worker reports once its deployment run has ended. A
 * worker is called directly where the test asserts its answer, as the reconcile master calls it; a
 * removal, a restart and the end of a run are left to the master running in this context, and the
 * test waits for the record to go, the VM to be replaced, or the outcome to be reported.
 */
@SpringBootTest
public class DeployWorkerTests extends KinoticTestBase {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final String NODE_ID = "worker-node";
    private static final String RUN_NODE_ID = "run-node";

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

    @Autowired
    private ProjectDependenciesRepository projectDependencies;

    @Autowired
    private ProjectRepository projects;

    @Autowired
    private DeploymentOperationsService deploymentOperations;

    @Autowired
    private VmNodeOrchestrationService nodeOrchestration;

    @Autowired
    private VmNodeRepository nodes;

    @Autowired
    private WorkloadRepository workloads;

    @Autowired
    private ParticipantIdentityService identities;

    @Autowired
    private ServiceRegistry serviceRegistry;

    @Autowired
    private JobRunRepository jobRuns;

    private final List<String> jobRunIds = new ArrayList<>();
    private final List<String> microserviceIds = new ArrayList<>();
    private final List<String> uiIds = new ArrayList<>();
    private final List<String> projectIds = new ArrayList<>();
    private StubVmManager vmManager;

    @AfterEach
    public void removeCreatedRecords() throws Exception {
        for (String id : microserviceIds) {
            MicroserviceDeployment deployment = await(microservices.findById(id));
            if (deployment != null) {
                if (deployment.getMachineIdentityId() != null) {
                    await(runAsOrganization(() -> identities.deleteById(deployment.getMachineIdentityId())));
                }
                await(microservices.deleteByIdSync(id));
            }
        }
        if (vmManager != null) {
            await(serviceRegistry.unregister(NodeFixtures.vmManagerAddress(NODE_ID)));
            vmManager = null;
            for (Workload workload : await(workloads.findAllForNode(NODE_ID, Pageable.create(0, 100, null))).getContent()) {
                await(workloads.deleteById(workload.getId()));
            }
            await(nodes.deleteByIdSync(NODE_ID));
        }
        for (String id : uiIds) {
            if (await(uis.findById(id)) != null) {
                await(uis.deleteByIdSync(id));
            }
        }
        for (String id : projectIds) {
            await(projectDependencies.deleteById(id, TEST_ORG_ID));
            if (await(projectDeployments.findById(id, TEST_ORG_ID)) != null) {
                await(projectDeployments.deleteByIdSync(id, TEST_ORG_ID));
            }
            if (await(projects.findById(id, TEST_ORG_ID)) != null) {
                await(projects.deleteByIdSync(id, TEST_ORG_ID));
            }
        }
        for (String id : jobRunIds) {
            await(jobRuns.deleteByIdSync(id));
        }
        microserviceIds.clear();
        uiIds.clear();
        projectIds.clear();
        jobRunIds.clear();
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
        await(projectDeployments.recordTarget(projectId, TEST_ORG_ID, new DeployTarget("node-x", "/srv/" + projectId, null, null, null)));
        await(projectDeployments.recordArtifacts(projectId, TEST_ORG_ID,
                                                 new ProjectArtifacts("old", List.of(new MicroserviceArtifact("api", "services/api", "index.ts")), List.of(), null),
                                                 false));
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
    public void aRestartReplacesTheRunningVmAtOnce() throws Exception {
        String projectId = "worker-restart";
        deployable(projectId);
        MicroserviceDeployment deployment = microservice(projectId, "api", new DeploymentState(DeploymentStatusType.RUNNING, COMMIT));
        // the master calls the worker for the intent, and the worker runs the VM on the project's node
        MicroserviceDeployment deployed = awaitDeployed(deployment.getId(), null);
        String firstRun = deployed.getWorkloadId();
        assertEquals(1, vmManager.started.size());

        await(runAsOrganization(() -> deploymentOperations.restartMicroservice(deployment.getId())));

        // the stop ends the run, its end brings the worker back, and a run that ended without failing is
        // replaced with no backoff
        MicroserviceDeployment replaced = awaitDeployed(deployment.getId(), firstRun);
        assertEquals(2, vmManager.started.size(), "a fresh VM took the place of the one stopped");
        assertNull(replaced.getRestartAt(), "a stop is not a failure, so nothing waits");
        assertEquals(WorkloadStatus.STOPPED, await(workloads.findById(firstRun)).getStatus());
        assertEquals(WorkloadStatus.RUNNING, await(workloads.findById(replaced.getWorkloadId())).getStatus());
        assertEquals(4 - ProjectWorkloadSizes.RUNTIME_CPUS, await(nodes.findById(NODE_ID)).getFreeCpus(), "one VM's room is held");
    }

    @Test
    public void aRestartOfAMicroserviceWithoutAVmRenewsItsIntent() throws Exception {
        MicroserviceDeployment deployment = microservice("worker-restart-renew", "api", new DeploymentState(DeploymentStatusType.RUNNING, COMMIT));
        // with no deployment target the worker fails the intent, and a failed answer waits for a new one
        MicroserviceDeployment failed = awaitAnswered(deployment.getId(), 1);
        assertEquals(DeploymentStatusType.FAILED, failed.getState().getObserved().phase());
        assertNull(failed.getWorkloadId());

        await(runAsOrganization(() -> deploymentOperations.restartMicroservice(deployment.getId())));

        // the intent is written again as new, and the worker answers the new generation
        MicroserviceDeployment renewed = awaitAnswered(deployment.getId(), 2);
        assertEquals(2, renewed.getState().getGeneration());
        assertEquals(DeploymentStatusType.FAILED, renewed.getState().getObserved().phase(), "still nothing to deploy to");
        assertTrue(renewed.getFailureMessage().contains("no deployment target"), renewed.getFailureMessage());
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
        await(projectDependencies.saveSync(new ProjectDependencies().setId(projectId)
                                                                    .setOrganizationId(TEST_ORG_ID)
                                                                    .setApplicationId(TEST_APP_ID)
                                                                    .setDependencyHash("hash-1")
                                                                    .setPackages(List.of("pkg:npm/express@5.1.0"))
                                                                    .setDirect(List.of(0)),
                                           TEST_ORG_ID));

        await(projectDeployments.requestDeletion(projectId, TEST_ORG_ID, "deletion of project " + projectId));

        awaitGone(() -> projectDeployments.findById(projectId, TEST_ORG_ID), "project deployment " + projectId);
        assertNull(await(projectDependencies.findById(projectId, TEST_ORG_ID)), "the project's SBOM goes with its deployment");
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

    @Test
    public void aDeployingProjectIsLeftDeployingWhileItsRunExecutes() throws Exception {
        String projectId = "worker-project-run-executing";
        JobRun run = deploying(projectId);

        assertEquals(Requeue.NONE, await(projectWorker.reconcile(await(projectDeployments.findById(projectId, TEST_ORG_ID)))));

        ProjectDeployment deployment = await(projectDeployments.findById(projectId, TEST_ORG_ID));
        assertEquals(DeploymentStatusType.DEPLOYING, deployment.getState().getObserved().phase());
        assertEquals(run.getId(), deployment.getLastJobRunId());
    }

    @Test
    public void aDeployingProjectReportsTheCommitOfItsCompletedRun() throws Exception {
        String projectId = "worker-project-run-completed";
        JobRun run = deploying(projectId);

        await(jobRuns.recordOutcome(run.getId(), ExecutionStatus.COMPLETED, null, new Date(), "test"));

        ProjectDeployment deployment = awaitUntil(() -> projectDeployments.findById(projectId, TEST_ORG_ID),
                                                  found -> found.getState().getObserved().phase() != DeploymentStatusType.DEPLOYING);
        assertEquals(new DeploymentState(DeploymentStatusType.RUNNING, COMMIT), deployment.getState().getObserved());
        assertEquals(deployment.getState().getGeneration(), deployment.getState().getObservedGeneration());
        assertNull(deployment.getFailureMessage());
        assertEquals(run.getId(), deployment.getLastJobRunId());
    }

    @Test
    public void aDeployingProjectReportsTheFailureOfItsFailedRun() throws Exception {
        String projectId = "worker-project-run-failed";
        JobRun run = deploying(projectId);

        await(jobRuns.recordOutcome(run.getId(), ExecutionStatus.FAILED, "java.lang.IllegalStateException: the build failed", new Date(), "test"));

        ProjectDeployment deployment = awaitUntil(() -> projectDeployments.findById(projectId, TEST_ORG_ID),
                                                  found -> found.getState().getObserved().phase() != DeploymentStatusType.DEPLOYING);
        // no commit was live before the run, and none is after it
        assertEquals(new DeploymentState(DeploymentStatusType.FAILED, null), deployment.getState().getObserved());
        assertEquals("java.lang.IllegalStateException: the build failed", deployment.getFailureMessage());
        // the failed intent is answered, so it waits for the next push
        assertEquals(deployment.getState().getGeneration(), deployment.getState().getObservedGeneration());
        assertEquals(run.getId(), deployment.getLastJobRunId());
    }

    @Test
    public void aDeployingProjectWhoseRunEndedWithItsNodeIsDeployedByANewRun() throws Exception {
        String projectId = "worker-project-run-abandoned";
        JobRun abandoned = deploying(projectId);
        Project project = new Project();
        project.setId(projectId);
        project.setOrganizationId(TEST_ORG_ID);
        project.setApplicationId(TEST_APP_ID);
        project.setName(projectId);
        await(projects.createSync(project, TEST_ORG_ID));

        // what the membership watch of another node writes when the node executing the run leaves
        await(jobRuns.recordAbandoned(abandoned.getId(),
                                      new StatusCondition(StatusConditionType.SERVER_NODE_LEFT,
                                                          "Node " + RUN_NODE_ID + " left the cluster while the run was live",
                                                          new Date()),
                                      "cluster membership"));

        ProjectDeployment restarted = awaitUntil(() -> projectDeployments.findById(projectId, TEST_ORG_ID),
                                                 found -> !abandoned.getId().equals(found.getLastJobRunId()));
        assertFalse(abandoned.getId().equals(restarted.getLastJobRunId()), "the worker never started a new run");
        jobRunIds.add(restarted.getLastJobRunId());
        JobRun next = await(jobRuns.findRun(restarted.getLastJobRunId()));
        assertNotNull(next, "the new run is recorded by the time the deployment names it and the worker has returned");
        assertEquals(new WatchedParent(WatchedType.PROJECT_DEPLOYMENT, TEST_ORG_ID, projectId), next.getState().getParent());

        // no node is registered, so the new run fails, and its end reaches the deployment through the master
        ProjectDeployment failed = awaitUntil(() -> projectDeployments.findById(projectId, TEST_ORG_ID),
                                              found -> found.getState().getObserved().phase() == DeploymentStatusType.FAILED);
        assertEquals(DeploymentStatusType.FAILED, failed.getState().getObserved().phase());
        assertNotNull(failed.getFailureMessage());
        assertEquals(ExecutionStatus.FAILED, await(jobRuns.findRun(restarted.getLastJobRunId())).getStatus());
    }

    /**
     * A deployment the worker has started a run for, with no project behind it: the run executing as
     * the job service records it, made by the deployment, which names it and reports DEPLOYING for
     * the intent of {@link #COMMIT}, whose artifacts the run's sync has reported.
     */
    private JobRun deploying(String projectId) throws Exception {
        ProjectDeployment deployment = projectDeployment(projectId, new DeploymentState(DeploymentStatusType.RUNNING, COMMIT));
        JobRun run = new JobRun().setId(UUID.randomUUID().toString())
                                 .setName("project-deploy-" + projectId)
                                 .setOrganizationId(TEST_ORG_ID)
                                 .setApplicationId(TEST_APP_ID)
                                 .setProjectId(projectId)
                                 .setNodeId(RUN_NODE_ID)
                                 .setStarted(new Date());
        run.getState().setParent(new WatchedParent(WatchedType.PROJECT_DEPLOYMENT, TEST_ORG_ID, projectId));
        jobRunIds.add(run.getId());
        await(jobRuns.saveSync(run));
        await(projectDeployments.recordArtifacts(projectId, TEST_ORG_ID, new ProjectArtifacts(COMMIT, List.of(), List.of(), null), false));
        await(projectDeployments.recordJobRun(projectId, TEST_ORG_ID, run.getId()));
        await(projectDeployments.reportObserved(projectId, TEST_ORG_ID, new DeploymentState(DeploymentStatusType.DEPLOYING, null),
                                                deployment.getState().getGeneration(), "deploy job " + run.getId()));
        return run;
    }

    /**
     * A project with a node to deploy to, served by a stub vm-manager, and the artifacts of {@link #COMMIT}
     * with one microservice, {@code api}: everything the microservice worker needs to run a VM.
     */
    private void deployable(String projectId) throws Exception {
        await(runAsOrganization(() -> nodeOrchestration.registerNode(NodeFixtures.registration(NODE_ID, 4, 4096, 10240))));
        vmManager = new StubVmManager();
        await(serviceRegistry.register(NodeFixtures.vmManagerAddress(NODE_ID), VmManager.class, vmManager));
        projectIds.add(projectId);
        Project project = new Project();
        project.setId(projectId);
        project.setOrganizationId(TEST_ORG_ID);
        project.setApplicationId(TEST_APP_ID);
        project.setName(projectId);
        await(projects.createSync(project, TEST_ORG_ID));
        projectDeployment(projectId, new DeploymentState(DeploymentStatusType.RUNNING, COMMIT));
        await(projectDeployments.recordTarget(projectId, TEST_ORG_ID, new DeployTarget(NODE_ID, "/srv/" + projectId, null, null, null)));
        await(projectDeployments.recordArtifacts(projectId, TEST_ORG_ID,
                                                 new ProjectArtifacts(COMMIT, List.of(new MicroserviceArtifact("api", "services/api", "index.ts")), List.of(), null),
                                                 false));
    }

    /** Waits for the master's worker to report the microservice deployed with a VM other than the given one. */
    private MicroserviceDeployment awaitDeployed(String id, String previousWorkloadId) throws Exception {
        MicroserviceDeployment ret = awaitUntil(() -> microservices.findById(id),
                                                deployment -> deployment.getState().getObserved() != null
                                                        && deployment.getState().getObserved().phase() == DeploymentStatusType.DEPLOYED
                                                        && deployment.getWorkloadId() != null
                                                        && !deployment.getWorkloadId().equals(previousWorkloadId));
        assertNotNull(ret, "microservice " + id + " is gone");
        assertNotNull(ret.getWorkloadId(), "the worker never deployed microservice " + id + ": " + describe(ret));
        return ret;
    }

    /** Waits for the master's worker to answer the given generation of the microservice's intent. */
    private MicroserviceDeployment awaitAnswered(String id, long generation) throws Exception {
        MicroserviceDeployment ret = awaitUntil(() -> microservices.findById(id),
                                                deployment -> deployment.getState().getObservedGeneration() >= generation);
        assertNotNull(ret, "microservice " + id + " is gone");
        assertTrue(ret.getState().getObservedGeneration() >= generation,
                   "the worker never answered generation " + generation + " of microservice " + id + ": " + describe(ret));
        return ret;
    }

    private static String describe(MicroserviceDeployment deployment) {
        return "generation " + deployment.getState().getGeneration() + " observed " + deployment.getState().getObservedGeneration()
                + " as " + deployment.getState().getObserved() + ", workload " + deployment.getWorkloadId()
                + ", failure " + deployment.getFailureMessage();
    }

    // The master ticks every two seconds; a deploy is a start on the stub node and a few writes
    private static <T> T awaitUntil(Supplier<Future<T>> read, Predicate<T> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        T ret = await(read.get());
        while ((ret == null || !condition.test(ret)) && System.currentTimeMillis() < deadline) {
            Thread.sleep(250);
            ret = await(read.get());
        }
        return ret;
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
}
