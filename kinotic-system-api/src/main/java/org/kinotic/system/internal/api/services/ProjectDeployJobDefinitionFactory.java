package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.ProjectDeployment;
import org.kinotic.management.api.model.ProjectRepoToken;
import org.kinotic.management.api.model.workload.VolumeMount;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.management.api.services.ProjectRepoTokenProvider;
import org.kinotic.domain.api.model.security.identity.MachineProvisionResult;
import org.kinotic.system.api.config.DeploymentProperties;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.system.api.services.VmNodeOrchestrationService;
import org.kinotic.system.api.services.WorkloadOrchestrationService;
import org.kinotic.system.api.model.deployment.DeployTarget;
import org.kinotic.system.api.model.deployment.ProjectDeployStores;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Creates the grind {@link JobDefinition} that deploys one commit of a project: resolve
 * the target node and checkout directory, bring the checkout to the commit with a
 * foreground sync workload, and ensure the long-lived runtime workload serving it.
 * The resolved {@link DeployTarget} and runtime workload id are stored in the job scope
 * under the {@link ProjectDeployStores} names, so the run's {@code TaskCompletedEvent}s
 * and {@code TaskRecord}s carry them to the caller and the console.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectDeployJobDefinitionFactory {

    private final VmNodeOrchestrationService vmNodeOrchestrationService;
    private final WorkloadOrchestrationService workloadOrchestrationService;
    private final ProjectRepoTokenProvider projectRepoTokenProvider;
    private final ProjectDeployIdentityService projectDeployIdentityService;
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
                // checkout lives on this node, the sync workload is deployed under its id - so a
                // resume must replay the recorded choice from the run's own records, never
                // re-derive it and risk landing on a different node
                .task(Tasks.fromCallable("Resolve deployment target",
                                         () -> resolveTarget(projectId, existing)
                                                 .toCompletionStage().toCompletableFuture()),
                      Store.state(ProjectDeployStores.DEPLOY_TARGET).wire())
                // Store.state: a resume after a later failure replays the synced checkout
                // rather than syncing it again
                .task(Tasks.fromCallable("Sync project source", new Callable<CompletableFuture<String>>() {

                    @Autowired
                    private DeployTarget target;

                    @Override
                    public CompletableFuture<String> call() {
                        return syncSource(project, target, commitSha);
                    }
                }), Store.state(ProjectDeployStores.SYNC_WORKLOAD_ID).wire())
                // wired so watchers of the run can tail the workload's logs mid-run, before
                // the deployment finishes and the id reaches the ProjectDeployment record; state
                // so the run's records name the workload afterwards, and a resume keeps it
                .task(Tasks.fromCallable("Ensure runtime workload", new Callable<CompletableFuture<String>>() {

                    @Autowired
                    private DeployTarget target;

                    @Override
                    public CompletableFuture<String> call() {
                        return ensureRuntimeWorkload(project, target).toCompletionStage().toCompletableFuture();
                    }
                }), Store.state(ProjectDeployStores.RUNTIME_WORKLOAD_ID).wire());
    }

    /**
     * Reuses the node and checkout directory of an existing deployment, retiring the sync
     * workload its last run left for inspection; a first deployment picks a node with the
     * capacity the sync workload needs and derives the checkout directory from the node's
     * advertised workload data directory. Either way the run's sync workload gets a fresh id.
     */
    private Future<DeployTarget> resolveTarget(String projectId, ProjectDeployment existing) {
        Future<DeployTarget> ret;
        String syncWorkloadId = UUID.randomUUID().toString();
        if (existing != null && existing.getNodeId() != null) {
            ret = destroyPreviousSyncWorkload(existing)
                    .map(v -> new DeployTarget(existing.getNodeId(),
                                               existing.getHostDir(),
                                               existing.getRuntimeWorkloadId(),
                                               syncWorkloadId));
        } else {
            Workload probe = new Workload();
            probe.setMemoryMb(deployment().getSyncMemoryMb());
            log.debug("Resolving deploy target for project {}: asking for a node with {} vcpus, {}MB memory, {}MB disk",
                     projectId, probe.getVcpus(), probe.getMemoryMb(), probe.getDiskSizeMb());
            ret = vmNodeOrchestrationService.findAvailableNode(probe.getVcpus(), probe.getMemoryMb(), probe.getDiskSizeMb())
                    .onFailure(error -> log.error("Placement query failed for project {}", projectId, error))
                    .compose(node -> {
                        log.info("Placement query for project {} returned {}", projectId,
                                 node != null ? node.getId() + " (workloadDataDir=" + node.getWorkloadDataDir() + ")" : "no node");
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
                                                     null,
                                                     syncWorkloadId));
                        }
                        return resolved;
                    });
        }
        return ret;
    }

    // The previous run's sync workload may already be gone - removed from the console, or
    // never recorded because that run failed before its target was known
    private Future<Void> destroyPreviousSyncWorkload(ProjectDeployment existing) {
        Future<Void> ret;
        if (existing.getSyncWorkloadId() == null) {
            ret = Future.succeededFuture();
        } else {
            ret = workloadOrchestrationService.destroyWorkload(existing.getSyncWorkloadId())
                    .recover(error -> {
                        log.warn("Previous sync workload {} of project {} could not be destroyed: {}",
                                 existing.getSyncWorkloadId(), existing.getId(), error.getMessage());
                        return Future.succeededFuture();
                    });
        }
        return ret;
    }

    /**
     * Runs the checkout-and-sync workload in the foreground on the target node. The
     * workload is kept after its run, whatever the outcome, so its logs stay inspectable
     * until the next deployment retires it; a failed run fails the job.
     */
    private CompletableFuture<String> syncSource(Project project, DeployTarget target, String commitSha) {
        return projectRepoTokenProvider.issueRepoToken(project.getOrganizationId(), project.getId())
                .compose(token -> projectDeployIdentityService.issueSyncCredentials(project)
                        .map(credentials -> syncWorkload(project, target, token, credentials, commitSha)))
                .compose(workloadOrchestrationService::deployWorkload)
                .compose(finished -> {
                    Future<String> ret;
                    if (finished.getStatus() == WorkloadStatus.STOPPED
                            && Integer.valueOf(0).equals(finished.getExitCode())) {
                        ret = Future.succeededFuture(finished.getId());
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
            ret = projectDeployIdentityService.issueRuntimeCredentials(project)
                    .compose(credentials -> workloadOrchestrationService.deployWorkload(
                            runtimeWorkload(project, target, credentials)))
                    .map(Workload::getId);
        }
        return ret;
    }

    private Workload syncWorkload(Project project,
                                  DeployTarget target,
                                  ProjectRepoToken token,
                                  MachineProvisionResult credentials,
                                  String commitSha) {
        DeploymentProperties deployment = deployment();
        Workload workload = new Workload("project-sync-" + project.getId(), deployment.getWorkloadRunnerImage());
        workload.setId(target.syncWorkloadId());
        workload.setDescription("Checkout and entity sync for project " + project.getId());
        workload.setNodeId(target.nodeId());
        workload.setOrganizationId(project.getOrganizationId());
        workload.setApplicationId(project.getApplicationId());
        workload.setDetached(false);
        workload.setMemoryMb(deployment.getSyncMemoryMb());
        workload.setEntrypoint(List.of("bun", "src/sync.ts"));
        workload.getEnvironment().put("GIT_CLONE_URL", token.getCloneUrl());
        workload.getEnvironment().put("GIT_REF", commitSha);
        putKinoticConnection(workload, deployment, credentials);
        workload.getSecrets().put("GIT_TOKEN", token.getToken());
        workload.getVolumeMounts().add(new VolumeMount().setHostPath(target.hostDir())
                                                        .setGuestPath("/workspace")
                                                        .setSizeLimitMb(deployment.getSyncMountLimitMb()));
        workload.getNetwork().setAllowedHosts(allowedHosts(deployment.getSyncAllowedHosts(), deployment));
        return workload;
    }

    private Workload runtimeWorkload(Project project, DeployTarget target, MachineProvisionResult credentials) {
        DeploymentProperties deployment = deployment();
        Workload workload = new Workload("project-runtime-" + project.getId(), deployment.getWorkloadRunnerImage());
        workload.setDescription("Microservice runtime for project " + project.getId());
        workload.setNodeId(target.nodeId());
        workload.setOrganizationId(project.getOrganizationId());
        workload.setApplicationId(project.getApplicationId());
        workload.setMemoryMb(deployment.getRuntimeMemoryMb());
        putKinoticConnection(workload, deployment, credentials);
        workload.getVolumeMounts().add(new VolumeMount().setHostPath(target.hostDir())
                                                        .setGuestPath("/app")
                                                        .setReadOnly(true));
        workload.getNetwork().setAllowedHosts(allowedHosts(deployment.getRuntimeAllowedHosts(), deployment));
        return workload;
    }

    /**
     * Configures how the workload reaches Kinotic and who it connects as. Requires the
     * workload's {@code organizationId} to already be set.
     */
    private static void putKinoticConnection(Workload workload,
                                             DeploymentProperties deployment,
                                             MachineProvisionResult credentials) {
        workload.getEnvironment().put("KINOTIC_SERVER_HOST", deployment.getServerHost());
        workload.getEnvironment().put("KINOTIC_SERVER_PORT", String.valueOf(deployment.getServerPort()));
        workload.getEnvironment().put("KINOTIC_SERVER_USE_SSL", String.valueOf(deployment.isServerUseSsl()));
        workload.getEnvironment().put("KINOTIC_ORGANIZATION_ID", workload.getOrganizationId());
        workload.getEnvironment().put("KINOTIC_CLIENT_ID", credentials.machine().getId());
        // a workload's environment is persisted verbatim and readable by anyone who can read
        // the workload back, so the secret travels as a secret, which the node injects into
        // the guest and never stores
        workload.getSecrets().put("KINOTIC_CLIENT_SECRET", credentials.clientSecret());
    }

    private static List<String> allowedHosts(List<String> workloadHosts, DeploymentProperties deployment) {
        List<String> hosts = new ArrayList<>(workloadHosts);
        hosts.add(deployment.getServerHost());
        return hosts;
    }

    private DeploymentProperties deployment() {
        return properties.getSystemApi().getDeployment();
    }

}
