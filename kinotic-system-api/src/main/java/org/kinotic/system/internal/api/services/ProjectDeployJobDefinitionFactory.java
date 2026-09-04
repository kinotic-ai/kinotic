package org.kinotic.system.internal.api.services;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.core.api.utils.ZoneUtil;
import org.kinotic.domain.api.model.DeploymentStatus;
import org.kinotic.domain.api.model.DeploymentStatusType;
import org.kinotic.domain.api.model.Organization;

import org.kinotic.management.api.model.MicroserviceArtifact;
import org.kinotic.management.api.model.MicroserviceDeployment;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.ProjectArtifacts;
import org.kinotic.management.api.model.ProjectDeployment;
import org.kinotic.management.api.model.ProjectRepoToken;
import org.kinotic.management.api.model.UiArtifact;
import org.kinotic.management.api.model.UiDeployment;
import org.kinotic.management.api.model.workload.VolumeMount;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.management.api.repositories.MicroserviceDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.system.api.config.UiDeploymentProperties;
import org.kinotic.system.api.services.OrganizationStorageService;
import org.kinotic.management.api.services.ProjectRepoTokenProvider;
import org.kinotic.system.api.services.UiDeploymentProvisioner;
import org.kinotic.system.api.services.UiStoragePaths;
import org.kinotic.system.api.services.WorkloadService;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.model.OrganizationStorage;
import org.kinotic.domain.api.model.security.identity.MachineProvisionResult;
import org.kinotic.domain.api.services.OrganizationService;
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
import org.kinotic.system.api.model.deployment.UiDeployments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Creates the grind {@link JobDefinition} that deploys one commit of a project: resolve
 * the target node and checkout directory, bring the checkout to the commit with a
 * foreground sync workload, bind the artifacts that workload found into the run, ensure
 * the organization's storage, ensure one long-lived runtime workload per microservice of
 * the commit, and publish its UIs. The resolved {@link DeployTarget}, the artifacts, the
 * microservice deployments and the UI deployments are stored in the job scope under the
 * {@link ProjectDeployStores} names, so the run's {@code TaskCompletedEvent}s and
 * {@code TaskRecord}s carry them to the caller and the console.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectDeployJobDefinitionFactory {

    /** Hostname labels are limited by DNS. */
    private static final int MAX_LABEL_LENGTH = 63;
    /** Longer than any upload takes, and short enough that a leaked URL is soon worthless. */
    private static final Duration UPLOAD_URL_TTL = Duration.ofHours(1);

    private final VmNodeOrchestrationService vmNodeOrchestrationService;
    private final WorkloadOrchestrationService workloadOrchestrationService;
    private final WorkloadService workloadService;
    private final ProjectRepoTokenProvider projectRepoTokenProvider;
    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final MicroserviceDeploymentRepository microserviceDeploymentRepository;
    private final UiDeploymentRepository uiDeploymentRepository;
    private final OrganizationService organizationService;
    private final OrganizationStorageService organizationStorageService;
    private final UiDeploymentProvisioner uiDeploymentProvisioner;
    private final ProjectDeployIdentityService projectDeployIdentityService;
    private final KinoticSystemApiProperties properties;
    private final KinoticDomainProperties domainProperties;

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
                }), Store.state(ProjectDeployStores.MICROSERVICE_DEPLOYMENTS).wire())
                // Store.state: the rows carry what the pass published, so a resume keeps them;
                // wired so the console lists each site as soon as the pass ends, and can tail the
                // publish workload's logs before that through the target
                .task(Tasks.fromCallable("Publish UIs", new Callable<CompletableFuture<UiDeployments>>() {

                    @Autowired
                    private DeployTarget target;

                    @Autowired
                    private ProjectArtifacts artifacts;

                    @Override
                    public CompletableFuture<UiDeployments> call() {
                        return publishUis(project, target, artifacts, commitSha).toCompletionStage().toCompletableFuture();
                    }
                }), Store.state(ProjectDeployStores.UI_DEPLOYMENTS).wire());
    }

    /**
     * Reuses the node and checkout directory of an existing deployment, retiring the sync and
     * publish workloads its last run left for inspection; a first deployment picks a node with
     * the capacity the sync workload needs and derives the checkout directory from the node's
     * advertised workload data directory. Either way the run's workloads get fresh ids.
     */
    private Future<DeployTarget> resolveTarget(String projectId, ProjectDeployment existing) {
        Future<DeployTarget> ret;
        String syncWorkloadId = UUID.randomUUID().toString();
        String uiPublishWorkloadId = UUID.randomUUID().toString();

        if (existing != null && existing.getNodeId() != null) {
            ret = destroyPreviousWorkload(existing.getSyncWorkloadId(), "sync", projectId)
                    .compose(v -> destroyPreviousWorkload(existing.getUiPublishWorkloadId(), "UI publish", projectId))
                    .map(v -> new DeployTarget(existing.getNodeId(),
                                               existing.getHostDir(),
                                               syncWorkloadId,
                                               uiPublishWorkloadId));
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
                                                     syncWorkloadId,
                                                     uiPublishWorkloadId));
                        }

                        return resolved;
                    });
        }

        return ret;
    }

    // The previous run's workload may already be gone - removed from the console, never
    // recorded because that run failed before its target was known, or never deployed
    // because that run had nothing to publish
    private Future<Void> destroyPreviousWorkload(String workloadId, String role, String projectId) {
        Future<Void> ret;
        if (workloadId == null) {
            ret = Future.succeededFuture();
        } else {
            ret = workloadOrchestrationService.destroyWorkload(workloadId)
                    .recover(error -> {
                        log.warn("Previous {} workload {} of project {} could not be destroyed: {}",
                                 role, workloadId, projectId, error.getMessage());
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
                .compose(finished -> requireSucceeded(finished, "Sync"))
                .toCompletionStage().toCompletableFuture();
    }

    /**
     * Passes a foreground workload's run only when it ran to completion; a run that was
     * stopped or failed fails the job. The workload is kept either way, so a failed run's
     * logs stay inspectable.
     */
    private static Future<String> requireSucceeded(Workload finished, String role) {
        Future<String> ret;
        if (finished.getStatus() == WorkloadStatus.COMPLETED) {
            ret = Future.succeededFuture(finished.getId());
        } else {
            ret = Future.failedFuture(new IllegalStateException(
                    role + " workload " + finished.getId() + " ended " + finished.getStatus()
                            + " with exit code " + finished.getExitCode()
                            + "; the workload is kept for log inspection"));
        }
        return ret;
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
                    return sequentially(artifacts.microservices(),
                                        artifact -> ensureMicroservice(project, target, artifact, unmatched.remove(artifact.name()), commitSha))
                            .compose(rows -> orphan(new ArrayList<>(unmatched.values()))
                            .map(orphans -> {
                                rows.addAll(orphans);
                                rows.sort(Comparator.comparing(MicroserviceDeployment::getName));
                                return rows;
                            }));
                })
                .compose(rows -> requireNoneFailed(rows,
                                                   row -> row.getStatus().type() == DeploymentStatusType.FAILED,
                                                   row -> row.getName() + ": " + row.getStatus().message(),
                                                   "Microservices of project " + project.getId() + " could not be deployed"))
                .map(MicroserviceDeployments::new);
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
        String entryPoint = artifact.dir() + "/" + artifact.entry();
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
                    .setStatus(new DeploymentStatus(DeploymentStatusType.FAILED,
                                                                "Deployment in progress"))
                    .setCreated(new Date())
                    .setUpdated(new Date()));
        }
        return deployment.compose(current -> ensureWorkload(project, target, current, entryPoint)
                .map(workloadId -> current.setWorkloadId(workloadId)
                                          .setEntryPoint(entryPoint)
                                          .setStatus(new DeploymentStatus(DeploymentStatusType.DEPLOYED)))
                .recover(error -> {
                    log.error("Microservice {} of project {} could not be deployed", artifact.name(), project.getId(), error);
                    return Future.succeededFuture(current.setStatus(new DeploymentStatus(
                            DeploymentStatusType.FAILED, error.getMessage())));
                })
                .compose(updated -> microserviceDeploymentRepository.save(updated.setCommitSha(commitSha)
                                                                                 .setUpdated(new Date()))));
    }

    /**
     * Leaves the microservice with a running workload: the recorded one when it is up and still
     * starts the same entry point, otherwise a new one.
     */
    private Future<String> ensureWorkload(Project project, DeployTarget target, MicroserviceDeployment deployment, String entryPoint) {
        Future<String> ret;
        if (deployment.getWorkloadId() == null) {
            ret = deployRuntimeWorkload(project, target, deployment, entryPoint);
        } else {
            ret = workloadService.findById(deployment.getWorkloadId())
                    .compose(existing -> {
                        Future<String> ensured;
                        WorkloadStatus status = existing != null ? existing.getStatus() : null;
                        boolean running = status == WorkloadStatus.RUNNING || status == WorkloadStatus.STARTING;
                        if (running && entryPoint.equals(deployment.getEntryPoint())) {
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
                                    .compose(v -> deployRuntimeWorkload(project, target, deployment, entryPoint));
                        } else {
                            ensured = deployRuntimeWorkload(project, target, deployment, entryPoint);
                        }
                        return ensured;
                    });
        }
        return ret;
    }

    private Future<String> deployRuntimeWorkload(Project project,
                                                 DeployTarget target,
                                                 MicroserviceDeployment deployment,
                                                 String entryPoint) {
        return projectDeployIdentityService.issueRuntimeCredentials(project, deployment)
                .compose(credentials -> workloadOrchestrationService.deployWorkload(
                        runtimeWorkload(project, target, deployment.getName(), entryPoint, credentials)))
                .map(Workload::getId);
    }

    /** Marks the deployments of microservices the commit no longer contains, leaving their workloads running. */
    private Future<List<MicroserviceDeployment>> orphan(List<MicroserviceDeployment> deployments) {
        List<Future<MicroserviceDeployment>> saves = new ArrayList<>();
        for (MicroserviceDeployment deployment : deployments) {
            if (deployment.getStatus().type() == DeploymentStatusType.ORPHANED) {
                saves.add(Future.succeededFuture(deployment));
            } else {
                saves.add(microserviceDeploymentRepository.save(deployment
                        .setStatus(new DeploymentStatus(DeploymentStatusType.ORPHANED))
                        .setUpdated(new Date())));
            }
        }
        return Future.all(saves).map(CompositeFuture::list);
    }

    /**
     * Leaves every UI of the deployed commit published and served: uploads the built UIs
     * through a foreground publish workload holding nothing but a short-lived upload URL,
     * then records the outcome on each UI's {@link UiDeployment}. A UI's first publish mints
     * its site's hostname label and provisions the site; a later publish keeps the site and
     * records the new commit, deleting the files of older ones. A UI the commit no longer
     * contains is marked orphaned and
     * keeps serving; one that returns is adopted. A commit without UIs publishes nothing. A
     * site that cannot be provisioned is recorded failed and the others still publish; the
     * task then fails naming the failed ones.
     */
    private Future<UiDeployments> publishUis(Project project, DeployTarget target, ProjectArtifacts artifacts, String commitSha) {
        return uiDeploymentRepository.findAllForProject(project.getId())
                .compose(existing -> {
                    Map<String, UiDeployment> unmatched = new HashMap<>();
                    existing.forEach(deployment -> unmatched.put(deployment.getName(), deployment));
                    Future<List<UiDeployment>> published;
                    if (artifacts.uis().isEmpty()) {
                        published = Future.succeededFuture(new ArrayList<>());
                    } else {
                        published = requireReadyStorage(project.getOrganizationId())
                                .compose(organization -> uploadUis(project, target, organization, commitSha)
                                        .compose(v -> finalizeUis(project, organization, artifacts, unmatched, commitSha)));
                    }
                    return published.compose(rows -> orphanUis(new ArrayList<>(unmatched.values()))
                            .map(orphans -> {
                                rows.addAll(orphans);
                                rows.sort(Comparator.comparing(UiDeployment::getName));
                                return rows;
                            }));
                })
                .compose(rows -> requireNoneFailed(rows,
                                                   row -> row.getStatus().type() == DeploymentStatusType.FAILED,
                                                   row -> row.getName() + ": " + row.getStatus().message(),
                                                   "UIs of project " + project.getId() + " could not be published"))
                .map(UiDeployments::new);
    }

    /**
     * The organization with the storage its UIs publish to, which was provisioned when the
     * organization was created. A deployment never provisions it: one that finds it not ready
     * fails naming the state, so nothing slow happens on Azure while publishing.
     */
    private Future<Organization> requireReadyStorage(String organizationId) {
        return organizationService.findById(organizationId)
                .map(organization -> {
                    if (organization == null) {
                        throw new IllegalStateException("Organization " + organizationId + " no longer exists");
                    }
                    OrganizationStorage storage = organization.getStorage();
                    DeploymentStatusType status = storage != null && storage.getStatus() != null ? storage.getStatus().type() : null;
                    if (status != DeploymentStatusType.READY) {
                        throw new IllegalStateException("Storage of organization " + organizationId + " is "
                                + (status == null ? "not provisioned" : status + (storage.getStatus().message() != null ? ": " + storage.getStatus().message() : ""))
                                + "; UIs cannot be published until it is ready");
                    }
                    return organization;
                });
    }

    private Future<String> uploadUis(Project project, DeployTarget target, Organization organization, String commitSha) {
        return organizationStorageService.issueUploadUrl(organization, project.getApplicationId(), UPLOAD_URL_TTL)
                .map(uploadUrl -> publishWorkload(project, target, uploadUrl, commitSha))
                .compose(workloadOrchestrationService::deployWorkload)
                .compose(finished -> requireSucceeded(finished, "UI publish"));
    }

    /**
     * Records each published UI once its files are up: the commit it now serves, the site it is
     * served from, minted and provisioned on first publish, and the cleanup of commits nobody
     * can still be looking at. Sequential, since minting labels races itself otherwise.
     */
    private Future<List<UiDeployment>> finalizeUis(Project project,
                                                  Organization organization,
                                                  ProjectArtifacts artifacts,
                                                  Map<String, UiDeployment> unmatched,
                                                  String commitSha) {
        return sequentially(artifacts.uis(),
                            ui -> finalizeUi(project, organization, ui, unmatched.remove(ui.name()), commitSha));
    }

    private Future<UiDeployment> finalizeUi(Project project,
                                            Organization organization,
                                            UiArtifact ui,
                                            UiDeployment existing,
                                            String commitSha) {
        Future<UiDeployment> deployment;
        if (existing == null) {
            // the files are up, so the site is ready once it serves this commit
            deployment = mintDeployment(project, ui)
                    .compose(minted -> uiDeploymentProvisioner.provision(minted.setCommitSha(commitSha), organization));
        } else if (existing.getStatus().type() == DeploymentStatusType.ORPHANED) {
            // the site never stopped serving, so the UI's return needs no provisioning
            deployment = Future.succeededFuture(existing.setStatus(new DeploymentStatus(DeploymentStatusType.READY)));
        } else {
            deployment = Future.succeededFuture(existing);
        }
        return deployment.compose(row -> deleteStaleFiles(organization, project.getApplicationId(), row.setCommitSha(commitSha))
                .compose(v -> uiDeploymentRepository.save(row.setUpdated(new Date()))));
    }

    /**
     * Mints the site's label, {@code <org>-<app>-<ui>}, taking the first free numeric suffix
     * when another site holds it, and its URL under the sites domain. The store enforces the
     * label's uniqueness on create.
     */
    private Future<UiDeployment> mintDeployment(Project project, UiArtifact ui) {
        String base = project.getOrganizationId() + "-" + project.getApplicationId() + "-" + ui.name();
        ZoneUtil.validateLabel(base);
        return mintWithSuffix(project, ui, base, 1);
    }

    private Future<UiDeployment> mintWithSuffix(Project project, UiArtifact ui, String base, int attempt) {
        String label = attempt == 1 ? base : base + "-" + attempt;
        Future<UiDeployment> ret;
        if (label.length() > MAX_LABEL_LENGTH) {
            ret = Future.failedFuture(new IllegalStateException("The hostname label " + label + " for UI " + ui.name()
                    + " of application " + project.getApplicationId() + " of organization " + project.getOrganizationId()
                    + " is longer than " + MAX_LABEL_LENGTH + " characters; shorten the application or UI name"));
        } else {
            ret = uiDeploymentRepository.create(new UiDeployment()
                            .setId(label)
                            .setUrl(uiDeployment().resolveSiteUrl(label))
                            .setOrganizationId(project.getOrganizationId())
                            .setApplicationId(project.getApplicationId())
                            .setProjectId(project.getId())
                            .setName(ui.name())
                            .setStatus(new DeploymentStatus(DeploymentStatusType.PROVISIONING))
                            .setCreated(new Date())
                            .setUpdated(new Date()))
                    .recover(error -> error instanceof AlreadyExistsException
                            ? mintWithSuffix(project, ui, base, attempt + 1)
                            : Future.failedFuture(error));
        }
        return ret;
    }

    // The index switched to the current commit, so nothing reaches another commit's files
    private Future<Void> deleteStaleFiles(Organization organization, String applicationId, UiDeployment row) {
        return organizationStorageService.deleteFilesOfOtherCommits(organization, UiStoragePaths.uiPrefix(applicationId, row.getName()), row.getCommitSha());
    }

    /** Marks the deployments of UIs the commit no longer contains, leaving their sites serving. */
    private Future<List<UiDeployment>> orphanUis(List<UiDeployment> deployments) {
        List<Future<UiDeployment>> saves = new ArrayList<>();
        for (UiDeployment deployment : deployments) {
            if (deployment.getStatus().type() == DeploymentStatusType.ORPHANED) {
                saves.add(Future.succeededFuture(deployment));
            } else {
                saves.add(uiDeploymentRepository.save(deployment
                        .setStatus(new DeploymentStatus(DeploymentStatusType.ORPHANED))
                        .setUpdated(new Date())));
            }
        }
        return Future.all(saves).map(CompositeFuture::list);
    }

    /**
     * The publish workload carries the built UIs to the organization's storage and nothing
     * else: no Kinotic credentials, no machine identity, a read-only checkout, and an egress
     * policy naming the storage account's host alone. Kept after its run, like the sync
     * workload, so its logs stay inspectable until the next run retires it.
     */
    private Workload publishWorkload(Project project,
                                     DeployTarget target,
                                     String uploadUrl,
                                     String commitSha) {
        DeploymentProperties deployment = deployment();
        Workload workload = new Workload("project-ui-publish-" + project.getId(), deployment.getWorkloadRunnerImage());
        workload.setId(target.uiPublishWorkloadId());
        workload.setDescription("UI publish for project " + project.getId());
        workload.setNodeId(target.nodeId());
        workload.setOrganizationId(project.getOrganizationId());
        workload.setApplicationId(project.getApplicationId());
        workload.setDetached(false);
        workload.setMemoryMb(deployment.getRuntimeMemoryMb());
        workload.setEntrypoint(List.of("bun", "src/publish-ui.ts"));
        workload.getEnvironment().put("KINOTIC_UI_COMMIT", commitSha);
        // the URL is a credential for the run's length, so it travels as a secret
        workload.getSecrets().put("KINOTIC_UI_UPLOAD_URL", uploadUrl);
        workload.getVolumeMounts().add(new VolumeMount().setHostPath(target.hostDir())
                                                        .setGuestPath("/workspace")
                                                        .setReadOnly(true));
        workload.getNetwork().setAllowedHosts(List.of(URI.create(uploadUrl).getHost()));
        return workload;
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
        // The UIs are built against the address a browser reaches the platform on, which the
        // egress address in DeploymentProperties.serverHost is not
        workload.getEnvironment().put("KINOTIC_UI_SERVER_URL", domainProperties.getDomain().resolveApiBaseUrl());
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
                                     String entryPoint,
                                     MachineProvisionResult credentials) {
        DeploymentProperties deployment = deployment();
        Workload workload = new Workload("project-runtime-" + project.getId() + "-" + microserviceName,
                                         deployment.getWorkloadRunnerImage());
        workload.setDescription("Microservice " + microserviceName + " of project " + project.getId());
        workload.setNodeId(target.nodeId());
        workload.setOrganizationId(project.getOrganizationId());
        workload.setApplicationId(project.getApplicationId());
        workload.setMemoryMb(deployment.getRuntimeMemoryMb());
        workload.getEnvironment().put("KINOTIC_APP_ENTRY", entryPoint);
        // The project's microservices export their traces and metrics through the node, grouped
        // under the project's name; the sync and publish workloads are steps of the run and
        // export nothing
        workload.setTelemetry(true);
        workload.getEnvironment().put("OTEL_SERVICE_NAME", project.getName());
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

    /**
     * Applies the operation to each item in turn, each one after the previous completed,
     * collecting the results in the items' order.
     */
    private static <A, R> Future<List<R>> sequentially(List<A> items, Function<A, Future<R>> operation) {
        Future<List<R>> ret = Future.succeededFuture(new ArrayList<>());
        for (A item : items) {
            ret = ret.compose(results -> operation.apply(item).map(result -> {
                results.add(result);
                return results;
            }));
        }
        return ret;
    }

    /**
     * Emits the rows unchanged unless any is failed, then fails naming every failed row as
     * {@code what: name: message; ...}.
     */
    private static <T> Future<List<T>> requireNoneFailed(List<T> rows, Predicate<T> failed, Function<T, String> describe, String what) {
        String failures = rows.stream().filter(failed).map(describe).collect(Collectors.joining("; "));
        Future<List<T>> ret;
        if (failures.isEmpty()) {
            ret = Future.succeededFuture(rows);
        } else {
            ret = Future.failedFuture(new IllegalStateException(what + ": " + failures));
        }
        return ret;
    }

    private DeploymentProperties deployment() {
        return properties.getSystemApi().getDeployment();
    }

    private UiDeploymentProperties uiDeployment() {
        return properties.getSystemApi().getUiDeployment();
    }

}
