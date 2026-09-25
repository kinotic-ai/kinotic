package org.kinotic.management.api.repositories;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.reconcile.ReconcilableRepository;
import org.kinotic.domain.api.reconcile.StatusCondition;
import org.kinotic.domain.api.reconcile.StatusConditionType;
import org.kinotic.domain.api.reconcile.WatchedType;
import org.kinotic.domain.api.model.DeploymentState;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.repositories.ReconcileStateRepository;
import org.kinotic.domain.internal.api.repositories.WatchedDocument;
import org.kinotic.domain.internal.api.repositories.WatchedIndex;
import org.kinotic.domain.internal.api.repositories.WatchedStateRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.management.api.model.MicroserviceDeployment;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores {@link MicroserviceDeployment}s, the standing deployments of a project's microservice
 * artifacts, listed by project. Every write is a partial or scripted update, so the state the
 * platform keeps on a record is never written back from a read copy.
 */
@Component
public class MicroserviceDeploymentRepository extends AbstractRepository<MicroserviceDeployment>
        implements ReconcilableRepository<MicroserviceDeployment> {

    public static final WatchedIndex WATCHED = new WatchedIndex(WatchedType.MICROSERVICE_DEPLOYMENT, "kinotic_microservice_deployment");

    /** More microservices than one project deploys, so a project's deployments are read in one page. */
    private static final int PROJECT_PAGE_SIZE = 500;

    private final WatchedStateRepository watchedStateRepository;
    private final ReconcileStateRepository reconcileStateRepository;

    public MicroserviceDeploymentRepository(CrudServiceTemplate crudServiceTemplate,
                                            WatchedStateRepository watchedStateRepository,
                                            ReconcileStateRepository reconcileStateRepository) {
        super(WATCHED.name(), MicroserviceDeployment.class, crudServiceTemplate);
        this.watchedStateRepository = watchedStateRepository;
        this.reconcileStateRepository = reconcileStateRepository;
    }

    @Override
    public WatchedType type() {
        return WATCHED.type();
    }

    // Stored by id alone; the organization is what the repository of the project deployment it
    // belongs to needs to find that
    @Override
    public String scopeOf(MicroserviceDeployment record) {
        return record.getOrganizationId();
    }

    @Override
    public Future<MicroserviceDeployment> find(String id, String scope) {
        return findById(id);
    }

    @Override
    public Future<Page<MicroserviceDeployment>> findDirty(Pageable pageable) {
        return watchedStateRepository.findDirty(indexName, type, pageable);
    }

    @Override
    public Future<Void> clearDirty(String id, String scope, long dirtyAt) {
        return watchedStateRepository.clearDirty(document(id), dirtyAt);
    }

    @Override
    public Future<Page<MicroserviceDeployment>> findUnreconciled(Pageable pageable) {
        return reconcileStateRepository.findUnreconciled(indexName, type, pageable);
    }

    /**
     * Lists the deployments of the project's microservices, ordered by name.
     *
     * @param projectId the project whose microservice deployments to list
     * @return a future emitting the deployments, empty when the project has none
     */
    public Future<List<MicroserviceDeployment>> findAllForProject(String projectId) {
        Validate.notBlank(projectId, "projectId cannot be blank");
        return findAll(Pageable.ofSize(PROJECT_PAGE_SIZE), b -> b.query(termFilter("projectId", projectId)))
                .map(page -> page.getContent().stream()
                                 .sorted(Comparator.comparing(MicroserviceDeployment::getName))
                                 .toList());
    }

    /**
     * Writes what the microservice's deployment should be, creating the record from {@code upsert} on
     * the microservice's first appearance in a commit, and enters the change in the ledger; visible to
     * search on completion.
     *
     * @param id      the deployment
     * @param desired what the deployment should be
     * @param upsert  the record to create when the microservice has never been deployed, or null when
     *                a missing record is an error
     * @param source  what caused it, for the ledger
     * @return the record as written, or as it stands when the intent was already in place
     */
    public Future<MicroserviceDeployment> updateDesired(String id, DeploymentState desired, MicroserviceDeployment upsert, String source) {
        Validate.notBlank(id, "id cannot be blank");
        Validate.isTrue(upsert == null || id.equals(upsert.getId()), "upsert must be the deployment %s", id);
        return reconcileStateRepository.updateDesired(document(id), desired, upsert, source)
                                       .compose(written -> written != null
                                               ? Future.succeededFuture(crudServiceTemplate.getObjectMapper().convertValue(written, MicroserviceDeployment.class))
                                               : findById(id));
    }

    /**
     * @see ReconcileStateRepository#renewDesired(WatchedDocument, String)
     */
    public Future<Void> renewDesired(String id, String source) {
        Validate.notBlank(id, "id cannot be blank");
        return reconcileStateRepository.renewDesired(document(id), source).mapEmpty();
    }

    /**
     * Writes what the microservice's deployment is and which generation of intent that answers, and
     * enters the change in the ledger; visible to search on completion.
     *
     * @param id       the deployment
     * @param observed what the deployment is
     * @param seen     the generation of intent the report answers
     * @param source   what caused it, for the ledger
     */
    public Future<Void> reportObserved(String id, DeploymentState observed, long seen, String source) {
        Validate.notBlank(id, "id cannot be blank");
        return reconcileStateRepository.reportObserved(document(id), observed, seen, source).mapEmpty();
    }

    /**
     * @see WatchedStateRepository#setCondition(WatchedDocument, StatusCondition, String)
     */
    public Future<Boolean> setCondition(String id, StatusCondition condition, String source) {
        return watchedStateRepository.setCondition(document(id), condition, source);
    }

    /**
     * @see WatchedStateRepository#clearCondition(WatchedDocument, StatusConditionType, String)
     */
    public Future<Boolean> clearCondition(String id, StatusConditionType type, String source) {
        return watchedStateRepository.clearCondition(document(id), type, source);
    }

    /**
     * @see ReconcileStateRepository#requestDeletion(WatchedDocument, String)
     */
    public Future<Void> requestDeletion(String id, String source) {
        return reconcileStateRepository.requestDeletion(document(id), source).mapEmpty();
    }

    /**
     * Records the workload running the microservice and the module it was started with, leaving
     * every other field as it is.
     */
    public Future<Void> recordWorkload(String id, String workloadId, String entryPoint) {
        Validate.notBlank(workloadId, "workloadId cannot be blank");
        Validate.notBlank(entryPoint, "entryPoint cannot be blank");
        Map<String, Object> fields = new HashMap<>();
        fields.put("workloadId", workloadId);
        fields.put("entryPoint", entryPoint);
        return partial(id, fields);
    }

    /**
     * Records the machine identity the microservice's workload authenticates as, leaving every other
     * field as it is.
     */
    public Future<Void> recordMachine(String id, String machineIdentityId) {
        Validate.notBlank(machineIdentityId, "machineIdentityId cannot be blank");
        Map<String, Object> fields = new HashMap<>();
        fields.put("machineIdentityId", machineIdentityId);
        return partial(id, fields);
    }

    /**
     * Records why the microservice is not running as it should, or clears it with null, leaving every
     * other field as it is.
     */
    public Future<Void> recordFailure(String id, String message) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("failureMessage", message);
        return partial(id, fields);
    }

    // The entity's own fields are merged in place, so the state the platform keeps is never written back from a read copy
    private Future<Void> partial(String id, Map<String, Object> fields) {
        Validate.notBlank(id, "id cannot be blank");
        fields.put("updated", new Date());
        return crudServiceTemplate.partialUpdateSync(indexName, id, fields, false);
    }

    private static WatchedDocument document(String id) {
        return WatchedDocument.of(WATCHED, id);
    }
}
