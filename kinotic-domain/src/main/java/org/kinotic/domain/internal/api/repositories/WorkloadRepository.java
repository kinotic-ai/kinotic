package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.workload.Workload;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.CompletableFuture;

@Repository
public class WorkloadRepository extends AbstractRepository<Workload> {

    public WorkloadRepository(ElasticsearchAsyncClient esAsyncClient,
                              CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_workload", Workload.class, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<Page<Workload>> findAllForNode(String nodeId, Pageable pageable) {
        return crudServiceTemplate.search(indexName, pageable, type,
                                          b -> b.query(q -> q.bool(bb -> bb.filter(
                                                  TermQuery.of(tq -> tq.field("nodeId").value(nodeId))._toQuery()))));
    }

    public CompletableFuture<Long> countForNode(String nodeId) {
        return crudServiceTemplate.count(indexName,
                                         b -> b.query(q -> q.bool(bb -> bb.filter(
                                                 TermQuery.of(tq -> tq.field("nodeId").value(nodeId))._toQuery()))));
    }
}
