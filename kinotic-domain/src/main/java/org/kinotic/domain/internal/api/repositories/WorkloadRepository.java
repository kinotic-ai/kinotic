package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.workload.Workload;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class WorkloadRepository extends AbstractRepository<Workload> {

    public WorkloadRepository(ElasticsearchAsyncClient esAsyncClient,
                              CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_workload", Workload.class, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<Page<Workload>> findAllForNode(String nodeId, Pageable pageable) {
        return findAll(pageable, b -> b.query(termFilter("nodeId", nodeId)));
    }

    public CompletableFuture<Long> countForNode(String nodeId) {
        return count(b -> b.query(termFilter("nodeId", nodeId)));
    }
}
