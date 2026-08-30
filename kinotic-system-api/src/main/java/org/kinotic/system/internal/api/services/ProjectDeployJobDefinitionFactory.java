package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.ProjectDeployment;
import org.kinotic.management.api.model.ProjectRepoToken;
import org.kinotic.management.api.model.workload.VolumeMount;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.management.api.services.ProjectRepoTokenProvider;
import org.kinotic.domain.api.model.security.MachineProvisionResult;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.system.api.config.DeploymentProperties;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.system.api.services.VmNodeOrchestrationService;
import org.kinotic.system.api.services.WorkloadOrchestrationService;
import org.kinotic.system.internal.api.model.deployment.DeployTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Creates the grind {@link JobDefinition} that deploys one commit of a project: resolve
 * the target node and checkout directory, bring the checkout to the commit with a
 * foreground sync workload, and ensure the long-lived runtime workload serving it.
 * The resolved {@link DeployTarget} and runtime workload id are stored in the job scope
 * under {@link #DEPLOY_TARGET} and {@link #RUNTIME_WORKLOAD_ID}, so the run's
 * {@code TaskCompletedEvent}s carry them to the caller.
 */
@Component
@RequiredArgsConstructor
public class ProjectDeployJobDefinitionFactory {

    /**
     * The job scope name the resolved {@link DeployTarget} is stored under.
     */
    public static final String DEPLOY_TARGET = "deployTarget";

    /**
     * The job scope name the id of the runtime workload serving the deployment is stored under.
     */
    public static final String RUNTIME_WORKLOAD_ID = "runtimeWorkloadId";

    private final VmNodeOrchestrationService vmNodeOrchestrationService;
    private final WorkloadOrchestrationService workloadOrchestrationService;
    private final ProjectRepoTokenProvider projectRepoTokenProvider;
    private final ParticipantIdentityService participantIdentityService;
    private final KinoticSystemApiProperties properties;

    /**
     * Creates the job definition deploying the given commit of the project.
     * The definition is one run's worth of tasks - create a fresh one for each run.
     *
     * @param project the project to deploy
     * @param existing the project's current {@link ProjectDeployment}, or {@code null} when
     *                 it has never been deployed
     * @param commitSha the commit to bring the node's checkout to
     * @return the assembled definition
     */
    public JobDefinition createJobDefinition(Project project, ProjectDeployment existing, String commitSha) {
        String projectId = project.getId();
        return JobDefinition.create("Deploy project " + projectId + " at " + commitSha)
                .name("project-deploy-" + projectId)
                .version("1.0.0")
                // Store.state: the target is a decision later effects are bound to - the sync
                // checkout lives on this node - so a resume must replay the recorded choice from
                // the run's own records, never re-derive it and risk landing on a different node
                .task(Tasks.fromCallable("Resolve deployment target",
                                         () -> resolveTarget(projectId, existing)
                                                 .toCompletionStage().toCompletableFuture()),
                      Store.state(DEPLOY_TARGET))
                .task(Tasks.fromCallable("Sync project source", new Callable<CompletableFuture<String>>() {

                    @Autowired
                    private DeployTarget target;

                    @Override
                    public CompletableFuture<String> call() {
                        return syncSource(project, target, commitSha);
                    }
                }))
                // wired so watchers of the run can tail the workload's logs mid-run, before
                // the deployment finishes and the id reaches the ProjectDeployment record
                .task(Tasks.fromCallable("Ensure runtime workload", new Callable<CompletableFuture<String>>() {

                    @Autowired
                    private DeployTarget target;

                    @Override
                    public CompletableFuture<String> call() {
                        return ensureRuntimeWorkload(project, target).toCompletionStage().toCompletableFuture();
                    }
                }), Store.result(RUNTIME_WORKLOAD_ID).wire());
    }

    /**
     * Reuses the node and checkout directory of an existing deployment; a first deployment
     * picks a node with the capacity the sync workload needs and derives the checkout
     * directory from the node's advertised workload data directory.
     */
    private Future<DeployTarget> resolveTarget(String projectId, ProjectDeployment existing) {
        Future<DeployTarget> ret;
        if (existing != null && existing.getNodeId() != null) {
            ret = Future.succeededFuture(new DeployTarget(existing.getNodeId(),
                                                          existing.getHostDir(),
                                                          existing.getRuntimeWorkloadId()));
        } else {
            Workload probe = new Workload();
            probe.setMemoryMb(deployment().getSyncMemoryMb());
            ret = vmNodeOrchestrationService.findAvailableNode(probe.getVcpus(), probe.getMemoryMb(), probe.getDiskSizeMb())
                    .compose(node -> {
                        Future<DeployTarget> resolved;
                        if (node == null) {
                            resolved = Future.failedFuture(new IllegalStateException(
                                    "No available node with sufficient resources to deploy project " + projectId));
                        } else if (node.getWorkloadDataDir() == null) {
                            resolved = Future.failedFuture(new IllegalStateException(
                                    "Node " + node.getId() + " does not advertise a workload data directory"));
                        } else {
                            resolved = Future.succeededFuture(
                                    new DeployTarget(node.getId(),
                                                     node.getWorkloadDataDir() + "/projects/" + projectId,
                                                     null));
                        }
                        return resolved;
                    });
        }
        return ret;
    }

    /**
     * Runs the checkout-and-sync workload in the foreground on the target node. A clean
     * exit destroys the workload, freeing its allocation; a failed run keeps it so its
     * logs remain inspectable, and fails the job.
     */
    private CompletableFuture<String> syncSource(Project project, DeployTarget target, String commitSha) {
        return projectRepoTokenProvider.issueRepoToken(project.getOrganizationId(), project.getId())
                .compose(token -> provisionMachine("project-deploy-" + project.getId(),
                                                   "Deploy sync for project " + project.getId(),
                                                   project.getOrganizationId())
                        .compose(machine -> workloadOrchestrationService.deployWorkload(
                                syncWorkload(project, target, token, machine, commitSha))))
                .compose(finished -> {
                    Future<String> ret;
                    if (finished.getStatus() == WorkloadStatus.STOPPED
                            && Integer.valueOf(0).equals(finished.getExitCode())) {
                        ret = workloadOrchestrationService.destroyWorkload(finished.getId())
                                                          .map(finished.getId());
                    } else {
                        ret = Future.failedFuture(new IllegalStateException(
                                "Sync workload " + finished.getId() + " ended " + finished.getStatus()
                                        + " with exit code " + finished.getExitCode()
                                        + "; the workload is kept for log inspection"));
                    }
                    return ret;
                })
                .toCompletionStage().toCompletableFuture();
    }

    private Future<String> ensureRuntimeWorkload(Project project, DeployTarget target) {
        Future<String> ret;
        if (target.runtimeWorkloadId() != null) {
            // The running supervisor picks the new commit up through the reload sentinel
            // the sync workload wrote — nothing to deploy
            ret = Future.succeededFuture(target.runtimeWorkloadId());
        } else {
            // The runtime machine's secret is minted only together with the workload it is
            // baked into: the workload is long-lived and reconnects with the same secret, so
            // rotating it anywhere else would strand the running guest
            ret = provisionMachine("project-runtime-" + project.getId(),
                                   "Runtime for project " + project.getId(),
                                   project.getOrganizationId())
                    .compose(machine -> workloadOrchestrationService.deployWorkload(
                            runtimeWorkload(project, target, machine)))
                    .map(Workload::getId);
        }
        return ret;
    }

    /**
     * Provisions the ORGANIZATION-scope machine identity a workload authenticates with,
     * returning it with a freshly generated secret.
     */
    private Future<MachineProvisionResult> provisionMachine(String machineId, String displayName, String organizationId) {
        // createMachine saves by id, so the deterministic id makes this one call both create
        // the identity on the first deploy and rotate its secret on every later one — the
        // plaintext secret is never stored, only handed to the workload being deployed
        return participantIdentityService.createMachine(
                (MachineParticipantIdentity) new MachineParticipantIdentity()
                        .setId(machineId)
                        .setOrganizationId(organizationId)
                        .setDisplayName(displayName));
    }

    private Workload syncWorkload(Project project, DeployTarget target, ProjectRepoToken token,
                                  MachineProvisionResult machine, String commitSha) {
        DeploymentProperties deployment = deployment();
        Workload workload = new Workload("project-sync-" + project.getId(), deployment.getWorkloadRunnerImage());
        workload.setDescription("Checkout and entity sync for project " + project.getId());
        workload.setNodeId(target.nodeId());
        workload.setOrganizationId(project.getOrganizationId());
        workload.setApplicationId(project.getApplicationId());
        workload.setDetached(false);
        workload.setMemoryMb(deployment.getSyncMemoryMb());
        workload.setEntrypoint(List.of("bun", "src/sync.ts"));
        workload.getEnvironment().put("GIT_CLONE_URL", token.getCloneUrl());
        workload.getEnvironment().put("GIT_REF", commitSha);
        putServerEnvironment(workload, deployment);
        // ORGANIZATION scope, because entity sync pushes into the management zone, which an
        // application participant cannot reach. KINOTIC_APPLICATION_ID stays unset - it would
        // narrow the connection to that application; the CLI reads the target application
        // from the checkout's own project configuration.
        putMachineEnvironment(workload, machine, project.getOrganizationId());
        workload.getSecrets().put("GIT_TOKEN", token.getToken());
        workload.getVolumeMounts().add(new VolumeMount().setHostPath(target.hostDir())
                                                        .setGuestPath("/workspace")
                                                        .setSizeLimitMb(deployment.getSyncMountLimitMb()));
        workload.getNetwork().setAllowedHosts(allowedHosts(deployment.getSyncAllowedHosts(), deployment));
        return workload;
    }

    private Workload runtimeWorkload(Project project, DeployTarget target, MachineProvisionResult machine) {
        DeploymentProperties deployment = deployment();
        Workload workload = new Workload("project-runtime-" + project.getId(), deployment.getWorkloadRunnerImage());
        workload.setDescription("Microservice runtime for project " + project.getId());
        workload.setNodeId(target.nodeId());
        workload.setOrganizationId(project.getOrganizationId());
        workload.setApplicationId(project.getApplicationId());
        workload.setMemoryMb(deployment.getRuntimeMemoryMb());
        putServerEnvironment(workload, deployment);
        putMachineEnvironment(workload, machine, project.getOrganizationId());
        // Names the zone the project's services publish into. Deliberately not
        // KINOTIC_APPLICATION_ID: that would ride into the credentials and scope the
        // connection to the application, while hosting services needs organization scope.
        workload.getEnvironment().put("KINOTIC_PROJECT_APPLICATION_ID", project.getApplicationId());
        workload.getVolumeMounts().add(new VolumeMount().setHostPath(target.hostDir())
                                                        .setGuestPath("/app")
                                                        .setReadOnly(true));
        workload.getNetwork().setAllowedHosts(allowedHosts(deployment.getRuntimeAllowedHosts(), deployment));
        return workload;
    }

    private static void putMachineEnvironment(Workload workload, MachineProvisionResult machine, String organizationId) {
        workload.getEnvironment().put("KINOTIC_CLIENT_ID", machine.machine().getId());
        workload.getEnvironment().put("KINOTIC_ORGANIZATION_ID", organizationId);
        // secrets, not environment: the workload record persists environment verbatim
        workload.getSecrets().put("KINOTIC_CLIENT_SECRET", machine.clientSecret());
    }

    private static void putServerEnvironment(Workload workload, DeploymentProperties deployment) {
        workload.getEnvironment().put("KINOTIC_SERVER_HOST", deployment.getServerHost());
        workload.getEnvironment().put("KINOTIC_SERVER_PORT", String.valueOf(deployment.getServerPort()));
        workload.getEnvironment().put("KINOTIC_SERVER_USE_SSL", String.valueOf(deployment.isServerUseSsl()));
    }

    private static List<String> allowedHosts(List<String> workloadHosts, DeploymentProperties deployment) {
        List<String> hosts = new ArrayList<>(workloadHosts);
        hosts.addAll(deployment.getServerAllowedHosts());
        return hosts;
    }

    private DeploymentProperties deployment() {
        return properties.getSystemApi().getDeployment();
    }

}
