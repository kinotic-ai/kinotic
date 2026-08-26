package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.domain.api.model.Project;
import org.kinotic.domain.api.model.ProjectDeployment;
import org.kinotic.domain.api.model.ProjectRepoToken;
import org.kinotic.domain.api.model.workload.VolumeMount;
import org.kinotic.domain.api.model.workload.Workload;
import org.kinotic.domain.api.model.workload.WorkloadStatus;
import org.kinotic.domain.api.services.ProjectRepoTokenProvider;
import org.kinotic.system.api.config.DeploymentProperties;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.model.grind.JobDefinition;
import org.kinotic.system.api.model.grind.Tasks;
import org.kinotic.system.api.services.VmNodeOrchestrationService;
import org.kinotic.system.api.services.WorkloadOrchestrationService;
import org.kinotic.system.internal.api.model.deployment.DeployTarget;
import org.kinotic.system.internal.api.model.deployment.ProjectDeployJob;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Assembles the grind {@link JobDefinition} that deploys one commit of a project: resolve
 * the target node and checkout directory, bring the checkout to the commit with a
 * foreground sync workload, and ensure the long-lived runtime workload serving it.
 */
@Component
@RequiredArgsConstructor
public class ProjectDeployJobDefinitionService {

    private final VmNodeOrchestrationService vmNodeOrchestrationService;
    private final WorkloadOrchestrationService workloadOrchestrationService;
    private final ProjectRepoTokenProvider projectRepoTokenProvider;
    private final KinoticSystemApiProperties properties;

    /**
     * Creates the single-use job deploying the given commit of the project.
     *
     * @param project the project to deploy
     * @param existing the project's current {@link ProjectDeployment}, or {@code null} when
     *                 it has never been deployed
     * @param commitSha the commit to bring the node's checkout to
     * @return the assembled job
     */
    public ProjectDeployJob createJob(Project project, ProjectDeployment existing, String commitSha) {
        String projectId = project.getId();
        // Tasks run sequentially and hand resolved state to later tasks through these
        // references, which also let the caller record how far the run got
        AtomicReference<DeployTarget> targetRef = new AtomicReference<>();
        AtomicReference<String> runtimeWorkloadIdRef = new AtomicReference<>();

        JobDefinition definition = JobDefinition.create("Deploy project " + projectId + " at " + commitSha)
                .name("project-deploy-" + projectId)
                .version("1.0.0")
                .task(Tasks.fromCallable("Resolve deployment target",
                                         () -> resolveTarget(projectId, existing)
                                                 .onSuccess(targetRef::set)
                                                 .toCompletionStage().toCompletableFuture()))
                .task(Tasks.fromCallable("Sync project source",
                                         () -> syncSource(project, targetRef, commitSha)))
                .task(Tasks.fromCallable("Ensure runtime workload",
                                         () -> ensureRuntimeWorkload(project, targetRef)
                                                 .onSuccess(runtimeWorkloadIdRef::set)
                                                 .toCompletionStage().toCompletableFuture()));

        return new ProjectDeployJob(definition, targetRef, runtimeWorkloadIdRef);
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
    private CompletableFuture<String> syncSource(Project project, AtomicReference<DeployTarget> targetRef, String commitSha) {
        return projectRepoTokenProvider.issueRepoToken(project.getOrganizationId(), project.getId())
                .compose(token -> workloadOrchestrationService.deployWorkload(
                        syncWorkload(project, targetRef.get(), token, commitSha)))
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

    private Future<String> ensureRuntimeWorkload(Project project, AtomicReference<DeployTarget> targetRef) {
        DeployTarget target = targetRef.get();
        Future<String> ret;
        if (target.runtimeWorkloadId() != null) {
            // The running supervisor picks the new commit up through the reload sentinel
            // the sync workload wrote — nothing to deploy
            ret = Future.succeededFuture(target.runtimeWorkloadId());
        } else {
            ret = workloadOrchestrationService.deployWorkload(runtimeWorkload(project, target))
                                              .map(Workload::getId);
        }
        return ret;
    }

    private Workload syncWorkload(Project project, DeployTarget target, ProjectRepoToken token, String commitSha) {
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
        // Machine credential distribution is not built yet: without KINOTIC_CLIENT_ID or
        // KINOTIC_TOKEN the runner checks out and builds but skips entity sync
        workload.getSecrets().put("GIT_TOKEN", token.getToken());
        workload.getVolumeMounts().add(new VolumeMount().setHostPath(target.hostDir())
                                                        .setGuestPath("/workspace")
                                                        .setSizeLimitMb(deployment.getSyncMountLimitMb()));
        workload.getNetwork().setAllowedHosts(allowedHosts(deployment.getSyncAllowedHosts(), deployment));
        return workload;
    }

    private Workload runtimeWorkload(Project project, DeployTarget target) {
        DeploymentProperties deployment = deployment();
        Workload workload = new Workload("project-runtime-" + project.getId(), deployment.getWorkloadRunnerImage());
        workload.setDescription("Microservice runtime for project " + project.getId());
        workload.setNodeId(target.nodeId());
        workload.setOrganizationId(project.getOrganizationId());
        workload.setApplicationId(project.getApplicationId());
        workload.setMemoryMb(deployment.getRuntimeMemoryMb());
        putServerEnvironment(workload, deployment);
        workload.getVolumeMounts().add(new VolumeMount().setHostPath(target.hostDir())
                                                        .setGuestPath("/app")
                                                        .setReadOnly(true));
        workload.getNetwork().setAllowedHosts(allowedHosts(deployment.getRuntimeAllowedHosts(), deployment));
        return workload;
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
