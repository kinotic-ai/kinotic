package org.kinotic.system.internal.api.services;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.management.api.model.MicroserviceArtifact;
import org.kinotic.management.api.model.MicroserviceDeployment;
import org.kinotic.management.api.model.MicroserviceDeploymentStatus;
import org.kinotic.management.api.model.MicroserviceDeploymentStatusType;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.ProjectArtifacts;
import org.kinotic.management.api.model.ProjectDeployment;
import org.kinotic.management.api.model.ProjectRepoToken;
import org.kinotic.management.api.model.workload.VolumeMount;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.management.api.repositories.MicroserviceDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.services.OrganizationStorageProvisioner;
import org.kinotic.management.api.services.ProjectRepoTokenProvider;
import org.kinotic.system.api.services.WorkloadService;
import org.kinotic.domain.api.model.security.identity.MachineProvisionResult;
import org.kinotic.system.api.config.DeploymentProperties;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.system.api.services.VmNodeOrchestrationService;
import org.kinotic.system.api.services.WorkloadOrchestrationService;
import org.kinotic.system.api.model.deployment.DeployTarget;
import org.kinotic.system.api.model.deployment.MicroserviceDeployments;
import org.kinotic.system.api.model.deployment.ProjectDeployStores;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Creates the grind {@link JobDefinition} that deploys one commit of a project: resolve
 * the target node and checkout directory, bring the checkout to the commit with a
 * foreground sync workload, bind the artifacts that workload found into the run, and
 * ensure one long-lived runtime workload per microservice of the commit. The resolved
 * {@link DeployTarget}, the artifacts and the microservice deployments are stored in the
 * job scope under the {@link ProjectDeployStores} names, so the run's
 * {@code TaskCompletedEvent}s and {@code TaskRecord}s carry them to the caller and the
 * console.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectDeployJobDefinitionFactory {

    private final VmNodeOrchestrationService vmNodeOrchestrationService;
    private final WorkloadOrchestrationService workloadOrchestrationService;
    private final WorkloadService workloadService;
    private final ProjectRepoTokenProvider projectRepoTokenProvider;
    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final MicroserviceDeploymentRepository microserviceDeploymentRepository;
    private final OrganizationStorageProvisioner organizationStorageProvisioner;
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
                // Store.state: what the sync workload found in the commit, bound to the run so a
                // resume replays it alongside the replayed sync; wired so the console lists it
                .task(Tasks.fromCallable("Resolve artifacts",
                                         () -> resolveArtifacts(project, commitSha)
                                                 .toCompletionStage().toCompletableFuture()),
                      Store.state(ProjectDeployStores.ARTIFACTS).wire())
                // Idempotent, so a resume may run it again; nothing to store, the organization
                // record carries the outcome
                .task(Tasks.fromCallable("Ensure organization storage", new Callable<CompletableFuture<Void>>() {

                    @Autowired
                    private ProjectArtifacts artifacts;

                    @Override
                    public CompletableFuture<Void> call() {
                        return ensureOrganizationStorage(project, artifacts).toCompletionStage().toCompletableFuture();
                    }
                }))
                // Store.state: the rows carry what the pass created, so a resume keeps them
                // rather than provisioning again; wired so the console lists each microservice's
                // workload as soon as the pass ends
                .task(Tasks.fromCallable("Ensure runtime workloads", new Callable<CompletableFuture<MicroserviceDeployments>>() {

                    @Autowired
                    private DeployTarget target;

                    @Autowired
                    private ProjectArtifacts artifacts;

                    @Override
                    public CompletableFuture<MicroserviceDeployments> call() {
                        return ensureRuntimeWorkloads(project, target, artifacts, commitSha)
                                .toCompletionStage().toCompletableFuture();
                    }
                }), Store.state(ProjectDeployStores.MICROSERVICE_DEPLOYMENTS).wire());
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

    /**
     * Binds the artifacts the sync workload reported for the commit into the run. The
     * workload reports them through {@code ProjectArtifactService.recordArtifacts} before it
     * writes the sentinel, so a record naming another commit means the report never arrived,
     * and the run fails rather than deploy against what an earlier commit contained.
     */
    private Future<ProjectArtifacts> resolveArtifacts(Project project, String commitSha) {
        return projectDeploymentRepository.findById(project.getId(), project.getOrganizationId())
                .map(deployment -> {
                    if (deployment == null || !commitSha.equals(deployment.getArtifactsCommitSha())) {
                        throw new IllegalStateException("The sync workload of project " + project.getId()
                                + " did not report the artifacts of commit " + commitSha);
                    }
                    return deployment.getArtifacts();
                });
    }

    /**
     * Leaves the organization with the storage its UIs publish to, provisioning it on the
     * organization's first deployment of a commit containing a UI. A commit without UIs needs
     * no storage and provisions none.
     */
    private Future<Void> ensureOrganizationStorage(Project project, ProjectArtifacts artifacts) {
        Future<Void> ret;
        if (artifacts.uis().isEmpty()) {
            ret = Future.succeededFuture();
        } else {
            ret = organizationStorageProvisioner.ensureStorage(project.getOrganizationId()).mapEmpty();
        }
        return ret;
    }

    /**
     * Leaves every microservice of the deployed commit with a runtime workload serving it from
     * the synced checkout, one VM and one machine identity each, and records the outcome on the
     * microservice's {@link MicroserviceDeployment}. A running workload is kept: its supervisor
     * picks the new commit up through the reload sentinel the sync workload wrote. One whose run
     * ended — stopped by hand or crashed — or whose entry module moved is replaced rather than
     * started again, so a deployment never reuses a VM whose state may be what failed it. A
     * microservice without a deployment gets one; a deployment whose microservice the commit
     * no longer contains is marked orphaned and left running. A microservice that cannot be
     * left running is recorded failed and the others still deploy; the task then fails naming
     * the failed ones.
     */
    private Future<MicroserviceDeployments> ensureRuntimeWorkloads(Project project,
                                                                   DeployTarget target,
                                                                   ProjectArtifacts artifacts,
                                                                   String commitSha) {
        return microserviceDeploymentRepository.findAllForProject(project.getId())
                .compose(existing -> {
                    Map<String, MicroserviceDeployment> unmatched = new HashMap<>();
                    existing.forEach(deployment -> unmatched.put(deployment.getName(), deployment));
                    // sequential: each microservice issues credentials and places a VM on the
                    // one node, and a failure must not stop the others from deploying
                    Future<List<MicroserviceDeployment>> ensured = Future.succeededFuture(new ArrayList<>());
                    for (MicroserviceArtifact artifact : artifacts.microservices()) {
                        MicroserviceDeployment current = unmatched.remove(artifact.name());
                        ensured = ensured.compose(rows -> ensureMicroservice(project, target, artifact, current, commitSha)
                                .map(row -> {
                                    rows.add(row);
                                    return rows;
                                }));
                    }
                    return ensured.compose(rows -> orphan(new ArrayList<>(unmatched.values()))
                            .map(orphans -> {
                                rows.addAll(orphans);
                                rows.sort(Comparator.comparing(MicroserviceDeployment::getName));
                                return rows;
                            }));
                })
                .compose(rows -> {
                    String failures = rows.stream()
                                          .filter(row -> row.getStatus().type() == MicroserviceDeploymentStatusType.FAILED)
                                          .map(row -> row.getName() + ": " + row.getStatus().message())
                                          .collect(Collectors.joining("; "));
                    Future<MicroserviceDeployments> ret;
                    if (failures.isEmpty()) {
                        ret = Future.succeededFuture(new MicroserviceDeployments(rows));
                    } else {
                        ret = Future.failedFuture(new IllegalStateException(
                                "Microservices of project " + project.getId() + " could not be deployed: " + failures));
                    }
                    return ret;
                });
    }

    /**
     * Ensures one microservice's workload and records the result on its deployment, creating
     * the deployment on the microservice's first appearance. The deployment is keyed by project
     * and name, so a duplicate create fails in the store rather than deploying twice.
     */
    private Future<MicroserviceDeployment> ensureMicroservice(Project project,
                                                              DeployTarget target,
                                                              MicroserviceArtifact artifact,
                                                              MicroserviceDeployment existing,
                                                              String commitSha) {
        String entry = artifact.dir() + "/" + artifact.entry();
        Future<MicroserviceDeployment> deployment;
        if (existing != null) {
            deployment = Future.succeededFuture(existing);
        } else {
            deployment = microserviceDeploymentRepository.create(new MicroserviceDeployment()
                    .setId(project.getId() + ":" + artifact.name())
                    .setOrganizationId(project.getOrganizationId())
                    .setApplicationId(project.getApplicationId())
                    .setProjectId(project.getId())
                    .setName(artifact.name())
                    .setStatus(new MicroserviceDeploymentStatus(MicroserviceDeploymentStatusType.FAILED,
                                                                "Deployment in progress"))
                    .setCreated(new Date())
                    .setUpdated(new Date()));
        }
        return deployment.compose(current -> ensureWorkload(project, target, current, entry)
                .map(workloadId -> current.setWorkloadId(workloadId)
                                          .setEntry(entry)
                                          .setStatus(new MicroserviceDeploymentStatus(MicroserviceDeploymentStatusType.DEPLOYED)))
                .recover(error -> {
                    log.error("Microservice {} of project {} could not be deployed", artifact.name(), project.getId(), error);
                    return Future.succeededFuture(current.setStatus(new MicroserviceDeploymentStatus(
                            MicroserviceDeploymentStatusType.FAILED, error.getMessage())));
                })
                .compose(updated -> microserviceDeploymentRepository.save(updated.setCommitSha(commitSha)
                                                                                 .setUpdated(new Date()))));
    }

    /**
     * Leaves the microservice with a running workload: the recorded one when it is up and still
     * starts the same entry, otherwise a new one.
     */
    private Future<String> ensureWorkload(Project project, DeployTarget target, MicroserviceDeployment deployment, String entry) {
        Future<String> ret;
        if (deployment.getWorkloadId() == null) {
            ret = deployRuntimeWorkload(project, target, deployment, entry);
        } else {
            ret = workloadService.findById(deployment.getWorkloadId())
                    .compose(existing -> {
                        Future<String> ensured;
                        WorkloadStatus status = existing != null ? existing.getStatus() : null;
                        boolean running = status == WorkloadStatus.RUNNING || status == WorkloadStatus.STARTING;
                        if (running && entry.equals(deployment.getEntry())) {
                            // The running supervisor picks the new commit up through the
                            // reload sentinel the sync workload wrote — nothing to deploy
                            ensured = Future.succeededFuture(existing.getId());
                        } else if (existing != null) {
                            ensured = workloadOrchestrationService.destroyWorkload(existing.getId())
                                    .recover(error -> {
                                        log.warn("Runtime workload {} of microservice {} of project {} could not be destroyed: {}",
                                                 existing.getId(), deployment.getName(), project.getId(), error.getMessage());
                                        return Future.succeededFuture();
                                    })
                                    .compose(v -> deployRuntimeWorkload(project, target, deployment, entry));
                        } else {
                            ensured = deployRuntimeWorkload(project, target, deployment, entry);
                        }
                        return ensured;
                    });
        }
        return ret;
    }

    private Future<String> deployRuntimeWorkload(Project project,
                                                 DeployTarget target,
                                                 MicroserviceDeployment deployment,
                                                 String entry) {
        return projectDeployIdentityService.issueRuntimeCredentials(project, deployment)
                .compose(credentials -> workloadOrchestrationService.deployWorkload(
                        runtimeWorkload(project, target, deployment.getName(), entry, credentials)))
                .map(Workload::getId);
    }

    /** Marks the deployments of microservices the commit no longer contains, leaving their workloads running. */
    private Future<List<MicroserviceDeployment>> orphan(List<MicroserviceDeployment> deployments) {
        List<Future<MicroserviceDeployment>> saves = new ArrayList<>();
        for (MicroserviceDeployment deployment : deployments) {
            if (deployment.getStatus().type() == MicroserviceDeploymentStatusType.ORPHANED) {
                saves.add(Future.succeededFuture(deployment));
            } else {
                saves.add(microserviceDeploymentRepository.save(deployment
                        .setStatus(new MicroserviceDeploymentStatus(MicroserviceDeploymentStatusType.ORPHANED))
                        .setUpdated(new Date())));
            }
        }
        return Future.all(saves).map(CompositeFuture::list);
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
        workload.getEnvironment().put("KINOTIC_PROJECT_ID", project.getId());
        putKinoticConnection(workload, deployment, credentials);
        workload.getSecrets().put("GIT_TOKEN", token.getToken());
        workload.getVolumeMounts().add(new VolumeMount().setHostPath(target.hostDir())
                                                        .setGuestPath("/workspace")
                                                        .setSizeLimitMb(deployment.getSyncMountLimitMb()));
        workload.getNetwork().setAllowedHosts(allowedHosts(deployment.getSyncAllowedHosts(), deployment));
        return workload;
    }

    private Workload runtimeWorkload(Project project,
                                     DeployTarget target,
                                     String microserviceName,
                                     String entry,
                                     MachineProvisionResult credentials) {
        DeploymentProperties deployment = deployment();
        Workload workload = new Workload("project-runtime-" + project.getId() + "-" + microserviceName,
                                         deployment.getWorkloadRunnerImage());
        workload.setDescription("Microservice " + microserviceName + " of project " + project.getId());
        workload.setNodeId(target.nodeId());
        workload.setOrganizationId(project.getOrganizationId());
        workload.setApplicationId(project.getApplicationId());
        workload.setMemoryMb(deployment.getRuntimeMemoryMb());
        workload.getEnvironment().put("KINOTIC_APP_ENTRY", entry);
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
