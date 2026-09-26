package org.kinotic.system.internal.api.services.deployment;

import io.vertx.core.Future;
import java.util.function.Supplier;
import org.kinotic.domain.api.model.Reconcilable;
import org.kinotic.management.api.model.deployment.DeploymentState;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.api.model.WatchedParent;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.management.api.model.deployment.DeploymentStatusType;

import org.kinotic.management.api.model.deployment.MicroserviceArtifact;
import org.kinotic.management.api.model.deployment.MicroserviceDeployment;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.deployment.ProjectArtifacts;
import org.kinotic.management.api.model.deployment.ProjectDeployment;
import org.kinotic.management.api.model.deployment.UiDeployment;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.management.api.repositories.MicroserviceDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.management.api.services.ProjectRepoTokenProvider;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.management.api.utils.ManagementApiUtil;
import org.kinotic.management.api.services.storage.OrganizationStorageService;
import org.kinotic.system.api.services.workload.VmNodeOrchestrationService;
import org.kinotic.system.api.services.workload.WorkloadOrchestrationService;
import org.kinotic.management.api.model.deployment.DeployTarget;
import org.kinotic.system.api.model.deployment.MicroserviceDeployments;
import org.kinotic.system.api.model.deployment.ProjectDeployStores;
import org.kinotic.system.api.model.deployment.UiDeployments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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
 * foreground sync workload, bind the artifacts that workload found into the run, ask for one
 * long-lived runtime workload per microservice of the commit and wait for the microservices'
 * workers to answer, upload its UIs and ask for their sites to serve them, and, unless the
 * environment has no organization storage, keep the project's SBOM current with a foreground SBOM
 * workload. The resolved {@link DeployTarget}, the artifacts, the microservice deployments, the UI
 * deployments and the SBOM are stored in the job scope under the {@link ProjectDeployStores}
 * names, so the run's {@code TaskCompletedEvent}s and {@code TaskRecord}s carry them to the
 * caller and the console.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectDeployJobDefinitionFactory {

    /** Longer than placing and booting every microservice's VM, or a first look at every site, takes. */
    private static final Duration CONVERGENCE_TIMEOUT = Duration.ofMinutes(10);
    /** Longer than generating and uploading an SBOM takes, and short enough that a leaked URL is soon worthless. */
    private static final Duration SBOM_UPLOAD_URL_TTL = Duration.ofHours(1);
    private static final Duration CONVERGENCE_POLL_INTERVAL = Duration.ofSeconds(2);

    private final Vertx vertx;
    private final VmNodeOrchestrationService vmNodeOrchestrationService;
    private final WorkloadOrchestrationService workloadOrchestrationService;
    private final ProjectRepoTokenProvider projectRepoTokenProvider;
    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final MicroserviceDeploymentRepository microserviceDeploymentRepository;
    private final UiDeploymentRepository uiDeploymentRepository;
    private final UiSitePublisher uiSitePublisher;
    private final ProjectDeployIdentityService projectDeployIdentityService;
    private final ProjectWorkloadFactory projectWorkloadFactory;
    private final OrganizationStorageService organizationStorageService;
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
        JobDefinition definition = JobDefinition.create("Deploy project " + projectId + " at " + commitSha)
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
                // Store.state: the rows carry what the pass left, so a resume keeps them rather
                // than asking again; wired so the console lists each microservice's workload as
                // soon as the pass ends
                .task(Tasks.fromCallable("Ensure runtime workloads", new Callable<CompletableFuture<MicroserviceDeployments>>() {

                    @Autowired
                    private ProjectArtifacts artifacts;

                    @Override
                    public CompletableFuture<MicroserviceDeployments> call() {
                        return ensureRuntimeWorkloads(project, artifacts, commitSha)
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
        if (!properties.getSystemApi().getDeployment().isDisableSbom()) {
            // Store.state: whether the run generated the SBOM, so a resume keeps the outcome rather
            // than generating it again; wired so the console shows it, and can tail the SBOM
            // workload's logs before that through the target
            definition.task(Tasks.fromCallable("Generate SBOM", new Callable<CompletableFuture<Boolean>>() {

                @Autowired
                private DeployTarget target;

                @Autowired
                private ProjectArtifacts artifacts;

                @Override
                public CompletableFuture<Boolean> call() {
                    return generateSbom(project, target, artifacts).toCompletionStage().toCompletableFuture();
                }
            }), Store.state(ProjectDeployStores.SBOM).wire());
        }
        return definition;
    }

    /**
     * Reuses the node and checkout directory of an existing deployment; a first deployment picks
     * a node with the capacity the sync workload needs and derives the checkout directory from
     * the node's advertised workload data directory. Either way the run's workloads get fresh ids.
     */
    private Future<DeployTarget> resolveTarget(String projectId, ProjectDeployment existing) {
        Future<DeployTarget> ret;
        String syncWorkloadId = UUID.randomUUID().toString();
        String uiPublishWorkloadId = UUID.randomUUID().toString();
        String sbomWorkloadId = UUID.randomUUID().toString();

        if (existing != null && existing.getNodeId() != null) {
            ret = Future.succeededFuture(new DeployTarget(existing.getNodeId(),
                                                          existing.getHostDir(),
                                                          syncWorkloadId,
                                                          uiPublishWorkloadId,
                                                          sbomWorkloadId));
        } else {
            log.debug("Resolving deploy target for project {}: asking for a node with {} cpus, {}MB memory, {}MB disk",
                     projectId, ProjectWorkloadSizes.SYNC_CPUS, ProjectWorkloadSizes.SYNC_MEMORY_MB, ProjectWorkloadSizes.SYNC_DISK_SIZE_MB);

            ret = vmNodeOrchestrationService.findAvailableNode(ProjectWorkloadSizes.SYNC_CPUS,
                                                               ProjectWorkloadSizes.SYNC_MEMORY_MB,
                                                               ProjectWorkloadSizes.SYNC_DISK_SIZE_MB)
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
                                                     uiPublishWorkloadId,
                                                     sbomWorkloadId));
                        }

                        return resolved;
                    });
        }

        return ret;
    }

    /**
     * Runs the checkout-and-sync workload in the foreground on the target node; a failed run
     * fails the job.
     */
    private CompletableFuture<String> syncSource(Project project, DeployTarget target, String commitSha) {
        return projectRepoTokenProvider.issueRepoToken(project.getOrganizationId(), project.getId())
                .compose(token -> projectDeployIdentityService.issueSyncCredentials(project)
                        .map(credentials -> projectWorkloadFactory.sync(project, target, token, credentials, commitSha)))
                .compose(workloadOrchestrationService::deployWorkload)
                .compose(finished -> requireSucceeded(finished, "Sync"))
                .toCompletionStage().toCompletableFuture();
    }

    /**
     * Passes a foreground workload's run only when it exited cleanly. The node has removed the
     * VM either way; the workload's record and logs stay as the run's outcome.
     */
    private static Future<String> requireSucceeded(Workload finished, String role) {
        Future<String> ret;
        if (finished.getStatus() == WorkloadStatus.STOPPED && Integer.valueOf(0).equals(finished.getExitCode())) {
            ret = Future.succeededFuture(finished.getId());
        } else {
            ret = Future.failedFuture(new IllegalStateException(
                    role + " workload " + finished.getId() + " ended " + finished.getStatus()
                            + " with exit code " + finished.getExitCode()));
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
                    if (deployment == null || deployment.getArtifacts() == null || !commitSha.equals(deployment.getArtifacts().commitSha())) {
                        throw new IllegalStateException("The sync workload of project " + project.getId()
                                + " did not report the artifacts of commit " + commitSha);
                    }
                    return deployment.getArtifacts();
                });
    }

    /**
     * Writes what every microservice of the deployed commit should be — running the commit, one
     * VM and one machine identity each — and what every microservice the commit no longer contains
     * should be, orphaned and left running, then waits for the microservices' workers to answer:
     * a running VM is kept and picks the new commit up through the reload sentinel the sync
     * workload wrote, one whose run ended or whose entry module moved gets a new VM, and a
     * microservice without a deployment gets one. A microservice that cannot be left running is
     * recorded failed and the others still deploy; the task then fails naming the failed ones, or
     * the ones whose worker gave no answer in time.
     */
    private Future<MicroserviceDeployments> ensureRuntimeWorkloads(Project project, ProjectArtifacts artifacts, String commitSha) {
        String source = "deploy of " + commitSha;
        return microserviceDeploymentRepository.findAllForProject(project.getId())
                .compose(existing -> {
                    Map<String, MicroserviceDeployment> unmatched = new HashMap<>();
                    existing.forEach(deployment -> unmatched.put(deployment.getName(), deployment));
                    return sequentially(artifacts.microservices(), artifact -> {
                                unmatched.remove(artifact.name());
                                return microserviceDeploymentRepository.updateDesired(deploymentId(project, artifact), new DeploymentState(DeploymentStatusType.DEPLOYED, commitSha),
                                                                                      newDeployment(project, artifact), source);
                            })
                            .compose(rows -> sequentially(new ArrayList<>(unmatched.values()),
                                                          orphan -> microserviceDeploymentRepository.updateDesired(orphan.getId(), new DeploymentState(DeploymentStatusType.ORPHANED, commitSha),
                                                                                                                   null, source)));
                })
                .compose(v -> awaitAnswered("Microservices of project " + project.getId(), () -> microserviceDeploymentRepository.findAllForProject(project.getId()),
                                            MicroserviceDeployment::getName, System.currentTimeMillis() + CONVERGENCE_TIMEOUT.toMillis()))
                .compose(rows -> requireNoneFailed(rows,
                                                   row -> row.getState().getObserved().phase() == DeploymentStatusType.FAILED,
                                                   row -> row.getName() + ": " + row.getFailureMessage(),
                                                   "Microservices of project " + project.getId() + " could not be deployed"))
                .map(MicroserviceDeployments::new);
    }

    private static String deploymentId(Project project, MicroserviceArtifact artifact) {
        return project.getId() + ":" + artifact.name();
    }

    // The record a microservice's first intent creates, belonging to the project's deployment
    private static MicroserviceDeployment newDeployment(Project project, MicroserviceArtifact artifact) {
        MicroserviceDeployment deployment = new MicroserviceDeployment()
                .setId(deploymentId(project, artifact))
                .setOrganizationId(project.getOrganizationId())
                .setApplicationId(project.getApplicationId())
                .setProjectId(project.getId())
                .setName(artifact.name())
                .setCreated(new Date())
                .setUpdated(new Date());
        deployment.getState().setParent(new WatchedParent(WatchedType.PROJECT_DEPLOYMENT, project.getOrganizationId(), project.getId()));
        return deployment;
    }

    /**
     * The listed deployments once every one's worker has answered its intent, listed again every
     * few seconds until then; fails naming the ones still unanswered at the deadline.
     */
    private <R extends Reconcilable<?>> Future<List<R>> awaitAnswered(String what, Supplier<Future<List<R>>> listing,
                                                                     Function<R, String> nameOf, long deadline) {
        return listing.get()
                .compose(rows -> {
                    List<String> unanswered = rows.stream()
                                                  .filter(row -> row.getState().getObservedGeneration() < row.getState().getGeneration())
                                                  .map(nameOf)
                                                  .toList();
                    Future<List<R>> ret;
                    if (unanswered.isEmpty()) {
                        ret = Future.succeededFuture(rows);
                    } else if (System.currentTimeMillis() >= deadline) {
                        ret = Future.failedFuture(new IllegalStateException(what + " were not answered within "
                                + CONVERGENCE_TIMEOUT.toMinutes() + " minutes: " + String.join(", ", unanswered)));
                    } else {
                        ret = vertx.timer(CONVERGENCE_POLL_INTERVAL.toMillis()).compose(id -> awaitAnswered(what, listing, nameOf, deadline));
                    }
                    return ret;
                });
    }

    /**
     * Uploads the built UIs of the deployed commit through a foreground publish workload holding
     * nothing but a short-lived upload URL per site, then writes what every UI's deployment should
     * be — serving the commit — and what every UI the commit no longer contains should be, orphaned
     * and still serving, and waits for the deployments' workers to answer. A UI's first publish mints
     * its site's hostname label; the site serves as soon as its files are up, which the worker keeps
     * checking. A commit without UIs publishes nothing.
     */
    private Future<UiDeployments> publishUis(Project project, DeployTarget target, ProjectArtifacts artifacts, String commitSha) {
        // nothing serves a site while the provisioner is disabled, so nothing is uploaded either
        boolean serving = !properties.getSystemApi().getUiDeployment().isDisableProvisioner();
        String source = "deploy of " + commitSha;
        return uiDeploymentRepository.findAllForProject(project.getId())
                .compose(existing -> {
                    Map<String, UiDeployment> unmatched = new HashMap<>();
                    existing.forEach(deployment -> unmatched.put(deployment.getName(), deployment));
                    Future<Void> published;
                    if (artifacts.uis().isEmpty()) {
                        published = Future.succeededFuture();
                    } else {
                        // a site's directory is its hostname, so a first publish mints the label first
                        published = sequentially(artifacts.uis(), ui -> uiSitePublisher.deploymentFor(project, ui, unmatched.remove(ui.name())))
                                .compose(rows -> (serving
                                                ? uiSitePublisher.upload(project, target, rows, commitSha)
                                                                 .compose(finished -> requireSucceeded(finished, "UI publish"))
                                                                 .mapEmpty()
                                                : Future.<Void>succeededFuture())
                                        .compose(v -> sequentially(rows, row -> uiDeploymentRepository.updateDesired(row.getId(), new DeploymentState(DeploymentStatusType.READY, commitSha), null, source))))
                                .mapEmpty();
                    }
                    return published.compose(v -> sequentially(new ArrayList<>(unmatched.values()),
                                                               orphan -> uiDeploymentRepository.updateDesired(orphan.getId(), new DeploymentState(DeploymentStatusType.ORPHANED, commitSha), null, source)));
                })
                .compose(v -> awaitAnswered("UIs of project " + project.getId(), () -> uiDeploymentRepository.findAllForProject(project.getId()),
                                            UiDeployment::getName, System.currentTimeMillis() + CONVERGENCE_TIMEOUT.toMillis()))
                .map(UiDeployments::new);
    }

    /**
     * Keeps the project's SBOM current: when the project's SBOM file does not list the dependencies
     * the sync workload reported, a foreground SBOM workload generates it from the checkout, uploads
     * it over that file in the organization storage account through a URL scoped to the file, and
     * records it; otherwise no workload runs and the SBOM stays. Emits whether this run generated
     * the SBOM, and fails when the checkout has no bun.lock.
     */
    private Future<Boolean> generateSbom(Project project, DeployTarget target, ProjectArtifacts artifacts) {
        return sbomGenerated(project)
                .compose(generated -> {
                    Future<Boolean> ret;
                    if (artifacts.dependencyHash() == null) {
                        ret = Future.failedFuture(new IllegalStateException("The checkout of project " + project.getId() + " at "
                                + artifacts.commitSha() + " has no bun.lock, which its SBOM is generated from"));
                    } else if (generated) {
                        // recordArtifacts clears the flag when the dependencies change, so a set flag
                        // means the SBOM lists the dependencies of this run
                        ret = Future.succeededFuture(false);
                    } else {
                        ret = organizationStorageService.issueWriteUrl(ManagementApiUtil.projectSbomFile(project.getOrganizationId(), project.getId()),
                                                                       SBOM_UPLOAD_URL_TTL)
                                // the sync workload has exited, so the SBOM workload takes over its machine's credentials
                                .compose(url -> projectDeployIdentityService.issueSyncCredentials(project)
                                        .map(credentials -> projectWorkloadFactory.sbom(project, target, credentials, url)))
                                .compose(workloadOrchestrationService::deployWorkload)
                                .compose(finished -> requireSucceeded(finished, "SBOM"))
                                .compose(workloadId -> sbomGenerated(project))
                                .map(recorded -> {
                                    if (!recorded) {
                                        throw new IllegalStateException("The SBOM workload of project " + project.getId()
                                                + " did not record the SBOM of " + artifacts.commitSha());
                                    }
                                    return true;
                                });
                    }
                    return ret;
                });
    }

    // Whether the project's SBOM file lists the dependencies its deployment records
    private Future<Boolean> sbomGenerated(Project project) {
        return projectDeploymentRepository.findById(project.getId(), project.getOrganizationId())
                .map(deployment -> deployment != null && deployment.isSbomGenerated());
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

}
