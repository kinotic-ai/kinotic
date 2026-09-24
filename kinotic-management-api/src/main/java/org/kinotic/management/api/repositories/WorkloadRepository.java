package org.kinotic.management.api.repositories;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.apache.commons.lang3.Validate;
import org.kinotic.management.api.model.workload.WorkloadStatus;

import java.util.List;
import java.util.stream.Stream;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkloadRepository extends AbstractRepository<Workload> {

    public WorkloadRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_workload", Workload.class, crudServiceTemplate);
    }

    public Future<Page<Workload>> findAllForNode(String nodeId, Pageable pageable) {
        return findAll(pageable, b -> b.query(termFilter("nodeId", nodeId)));
    }

    /**
     * Finds the workloads that run on behalf of the organization, those of its applications included.
     */
    public Future<Page<Workload>> findAllForOrganization(String organizationId, Pageable pageable) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return findAll(pageable, b -> b.query(termFilter("organizationId", organizationId)));
    }

    /**
     * Finds the workloads that run on behalf of one application of the organization.
     */
    public Future<Page<Workload>> findAllForApplication(String organizationId, String applicationId, Pageable pageable) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        return findAll(pageable, b -> b.query(composeFilter(termFilter("organizationId", organizationId),
                                                            termFilter("applicationId", applicationId))));
    }

    /**
     * Counts the workloads on a node whose run has not ended: the ones still holding a VM there.
     */
    public Future<Long> countRunningForNode(String nodeId) {
        List<FieldValue> running = Stream.of(WorkloadStatus.STARTING, WorkloadStatus.RUNNING, WorkloadStatus.STOPPING)
                                         .map(status -> FieldValue.of(status.name()))
                                         .toList();
        return count(b -> b.query(composeFilter(termFilter("nodeId", nodeId),
                                                Query.of(q -> q.terms(t -> t.field("status").terms(v -> v.value(running)))))));
    }
}
