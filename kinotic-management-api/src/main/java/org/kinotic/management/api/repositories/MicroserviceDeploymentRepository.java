package org.kinotic.management.api.repositories;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.DeploymentState;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.internal.api.repositories.AbstractReconcilableRepository;
import org.kinotic.domain.internal.api.repositories.ReconcileStateRepository;
import org.kinotic.domain.internal.api.repositories.WatchEventRepository;
import org.kinotic.domain.internal.api.repositories.WatchedDocument;
import org.kinotic.domain.internal.api.repositories.WatchedIndex;
import org.kinotic.domain.internal.api.repositories.WatchedStateRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.management.api.model.deployment.MicroserviceDeployment;
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
public class MicroserviceDeploymentRepository extends AbstractReconcilableRepository<MicroserviceDeployment, DeploymentState> {

    private static final WatchedIndex WATCHED = new WatchedIndex(WatchedType.MICROSERVICE_DEPLOYMENT, "kinotic_microservice_deployment");

    /** More microservices than one project deploys, so a project's deployments are read in one page. */
    private static final int PROJECT_PAGE_SIZE = 500;

    public MicroserviceDeploymentRepository(CrudServiceTemplate crudServiceTemplate,
                                            WatchedStateRepository watchedStateRepository,
                                            WatchEventRepository watchEventRepository,
                                            ReconcileStateRepository reconcileStateRepository) {
        super(WATCHED, MicroserviceDeployment.class, crudServiceTemplate, watchedStateRepository, watchEventRepository, reconcileStateRepository);
    }

    @Override
    public String scopeOf(MicroserviceDeployment record) {
        return record.getOrganizationId();
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
     * @see ReconcileStateRepository#renewDesired(WatchedDocument, String)
     */
    public Future<Void> renewDesired(String id, String source) {
        Validate.notBlank(id, "id cannot be blank");
        return reconcileStateRepository.renewDesired(document(id), source).mapEmpty();
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
     * Records why the microservice is not running as it should, or clears it with null, and clears
     * any wait for a restart, leaving every other field as it is.
     */
    public Future<Void> recordFailure(String id, String message) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("failureMessage", message);
        fields.put("restartAt", null);
        return partial(id, fields);
    }

    /**
     * Records why the microservice's VM exited and when it is started again, leaving every other
     * field as it is.
     */
    public Future<Void> recordRestartWait(String id, String message, Date restartAt) {
        Validate.notNull(restartAt, "restartAt cannot be null");
        Map<String, Object> fields = new HashMap<>();
        fields.put("failureMessage", message);
        fields.put("restartAt", restartAt);
        return partial(id, fields);
    }

    // The entity's own fields are merged in place, so the state the platform keeps is never written back from a read copy
    private Future<Void> partial(String id, Map<String, Object> fields) {
        Validate.notBlank(id, "id cannot be blank");
        fields.put("updated", new Date());
        return crudServiceTemplate.partialUpdateSync(indexName, id, fields, false);
    }

}
