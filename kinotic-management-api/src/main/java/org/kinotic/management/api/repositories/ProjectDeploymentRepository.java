package org.kinotic.management.api.repositories;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.repositories.ReconcilableRepository;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.management.api.model.deployment.DeploymentState;
import org.kinotic.domain.internal.api.repositories.AbstractApplicationScopedRepository;
import org.kinotic.domain.internal.api.repositories.ReconcileStateRepository;
import org.kinotic.domain.internal.api.repositories.WatchEventRepository;
import org.kinotic.domain.internal.api.repositories.WatchedDocument;
import org.kinotic.domain.internal.api.repositories.WatchedIndex;
import org.kinotic.domain.internal.api.repositories.WatchedStateRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.management.api.model.deployment.ProjectArtifacts;
import org.kinotic.management.api.model.deployment.ProjectDeployment;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class ProjectDeploymentRepository extends AbstractApplicationScopedRepository<ProjectDeployment>
        implements ReconcilableRepository<ProjectDeployment> {

    public static final WatchedIndex WATCHED = new WatchedIndex(WatchedType.PROJECT_DEPLOYMENT, "kinotic_project_deployment");

    private final WatchedStateRepository watchedStateRepository;
    private final WatchEventRepository watchEventRepository;
    private final ReconcileStateRepository reconcileStateRepository;

    public ProjectDeploymentRepository(CrudServiceTemplate crudServiceTemplate,
                                       WatchedStateRepository watchedStateRepository,
                                       WatchEventRepository watchEventRepository,
                                       ReconcileStateRepository reconcileStateRepository) {
        super(WATCHED.name(), ProjectDeployment.class, crudServiceTemplate);
        this.watchedStateRepository = watchedStateRepository;
        this.watchEventRepository = watchEventRepository;
        this.reconcileStateRepository = reconcileStateRepository;
    }

    /**
     * Lists what happened to the project's deployment and to the records it made, the deployment
     * jobs, build VMs, microservice deployments and UI deployments, newest first.
     *
     * @param projectId the project the deployment belongs to
     * @param orgId     the organization the project belongs to
     * @param pageable  the page to return
     * @return a future emitting a page of ledger entries, empty when the project has never been deployed
     */
    public Future<Page<WatchEvent>> findHistory(String projectId, String orgId, Pageable pageable) {
        Validate.notBlank(projectId, "projectId cannot be blank");
        Validate.notBlank(orgId, "orgId cannot be blank");
        return watchEventRepository.findHistory(WATCHED.type(), orgId, projectId, pageable);
    }

    @Override
    public WatchedType type() {
        return WATCHED.type();
    }

    @Override
    public String scopeOf(ProjectDeployment record) {
        return record.getOrganizationId();
    }

    @Override
    public Future<ProjectDeployment> find(String id, String scope) {
        return findById(id, scope);
    }

    @Override
    public Future<Page<ProjectDeployment>> findDirty(Pageable pageable) {
        return watchedStateRepository.findDirty(indexName, type, pageable);
    }

    @Override
    public Future<Void> clearDirty(String id, String scope, long dirtyAt) {
        return watchedStateRepository.clearDirty(document(id, scope), dirtyAt);
    }

    @Override
    public Future<Page<ProjectDeployment>> findUnreconciled(Pageable pageable) {
        return reconcileStateRepository.findUnreconciled(indexName, type, pageable);
    }

    // Every organization's deployments, which the scoped listing cannot give: the master's one look at each
    @Override
    public Future<Page<ProjectDeployment>> findAll(Pageable pageable) {
        return crudServiceTemplate.search(indexName, pageable, type, null);
    }

    /**
     * Writes what the project's deployment should be, creating the record from {@code upsert} on the
     * project's first deployment, and enters the change in the ledger; visible to search on completion.
     *
     * @param projectId the project
     * @param orgId     the organization the project belongs to
     * @param desired   what the deployment should be
     * @param upsert    the record to create when the project has never been deployed
     * @param source    what caused it, for the ledger
     * @return the record as written, or as it stands when the intent was already in place
     */
    public Future<ProjectDeployment> updateDesired(String projectId, String orgId, DeploymentState desired,
                                                   ProjectDeployment upsert, String source) {
        Validate.notBlank(projectId, "projectId cannot be blank");
        Validate.notBlank(orgId, "orgId cannot be blank");
        Validate.notNull(upsert, "upsert cannot be null");
        Validate.isTrue(projectId.equals(upsert.getId()) && orgId.equals(upsert.getOrganizationId()),
                        "upsert must be the deployment of project %s in organization %s", projectId, orgId);
        return reconcileStateRepository.updateDesired(document(projectId, orgId), desired, upsert, source)
                                       .compose(written -> written != null
                                               ? Future.succeededFuture(crudServiceTemplate.getObjectMapper().convertValue(written, ProjectDeployment.class))
                                               : findById(projectId, orgId));
    }

    /**
     * Writes what the project's deployment is and which generation of intent that answers, and
     * enters the change in the ledger; visible to search on completion.
     *
     * @param projectId the project
     * @param orgId     the organization the project belongs to
     * @param observed  what the deployment is
     * @param seen      the generation of intent the report answers
     * @param source    what caused it, for the ledger
     */
    public Future<Void> reportObserved(String projectId, String orgId, DeploymentState observed, long seen, String source) {
        Validate.notBlank(projectId, "projectId cannot be blank");
        Validate.notBlank(orgId, "orgId cannot be blank");
        return reconcileStateRepository.reportObserved(document(projectId, orgId), observed, seen, source).mapEmpty();
    }

    /**
     * @see ReconcileStateRepository#requestDeletion(WatchedDocument, String)
     */
    public Future<Void> requestDeletion(String projectId, String orgId, String source) {
        Validate.notBlank(projectId, "projectId cannot be blank");
        Validate.notBlank(orgId, "orgId cannot be blank");
        return reconcileStateRepository.requestDeletion(document(projectId, orgId), source).mapEmpty();
    }

    /**
     * Records the job run deploying the project, leaving every other field as it is.
     */
    public Future<Void> recordJobRun(String projectId, String orgId, String jobRunId) {
        Validate.notBlank(jobRunId, "jobRunId cannot be blank");
        Map<String, Object> fields = new HashMap<>();
        fields.put("lastJobRunId", jobRunId);
        return partial(projectId, orgId, fields);
    }

    /**
     * Records where a deployment run put the project and the workloads it ran, leaving every other
     * field as it is.
     */
    public Future<Void> recordTarget(String projectId, String orgId, String nodeId, String hostDir,
                                     String syncWorkloadId, String uiPublishWorkloadId) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("nodeId", nodeId);
        fields.put("hostDir", hostDir);
        fields.put("syncWorkloadId", syncWorkloadId);
        fields.put("uiPublishWorkloadId", uiPublishWorkloadId);
        return partial(projectId, orgId, fields);
    }

    /**
     * Records the machine identity the project's sync workload authenticates as, leaving every other
     * field as it is.
     */
    public Future<Void> recordSyncMachine(String projectId, String orgId, String machineIdentityId) {
        Validate.notBlank(machineIdentityId, "machineIdentityId cannot be blank");
        Map<String, Object> fields = new HashMap<>();
        fields.put("syncMachineIdentityId", machineIdentityId);
        return partial(projectId, orgId, fields);
    }

    /**
     * Records the artifacts the sync workload found in a commit, leaving every other field as it is.
     */
    public Future<Void> recordArtifacts(String projectId, String orgId, ProjectArtifacts artifacts, String commitSha) {
        Validate.notNull(artifacts, "artifacts cannot be null");
        Validate.notBlank(commitSha, "commitSha cannot be blank");
        Map<String, Object> fields = new HashMap<>();
        fields.put("artifacts", artifacts);
        fields.put("artifactsCommitSha", commitSha);
        return partial(projectId, orgId, fields);
    }

    /**
     * Records why the last deployment failed, or clears it with null, leaving every other field as
     * it is.
     */
    public Future<Void> recordFailure(String projectId, String orgId, String message) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("failureMessage", message);
        return partial(projectId, orgId, fields);
    }

    // The entity's own fields are merged in place, so the state the platform keeps is never written back from a read copy
    private Future<Void> partial(String projectId, String orgId, Map<String, Object> fields) {
        Validate.notBlank(projectId, "projectId cannot be blank");
        Validate.notBlank(orgId, "orgId cannot be blank");
        fields.put("updated", new Date());
        return crudServiceTemplate.partialUpdateSync(indexName, composeDocumentId(projectId, orgId), fields, false,
                                                     u -> u.routing(orgId));
    }

    private WatchedDocument document(String projectId, String orgId) {
        return new WatchedDocument(WATCHED, projectId, composeDocumentId(projectId, orgId), orgId);
    }
}
