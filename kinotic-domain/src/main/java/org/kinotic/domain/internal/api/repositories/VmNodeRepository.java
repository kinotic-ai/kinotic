package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.workload.VmNode;
import org.kinotic.domain.api.model.workload.VmNodeStatus;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.CompletableFuture;

@Repository
public class VmNodeRepository extends AbstractRepository<VmNode> {

    public VmNodeRepository(ElasticsearchAsyncClient esAsyncClient,
                            CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_vm_node", VmNode.class, esAsyncClient, crudServiceTemplate);
    }

    /**
     * Returns the first page (up to 100) of nodes whose {@code status} is {@link VmNodeStatus#ONLINE}.
     * Resource-availability filtering is left to the caller because Elasticsearch can't express the
     * "available = total - allocated" computation as a server-side query.
     */
    public CompletableFuture<Page<VmNode>> findOnlineNodes() {
        return findAll(Pageable.create(0, 100, null),
                        b -> b.query(q -> q.bool(bb -> bb.filter(
                                TermQuery.of(tq -> tq.field("status").value(VmNodeStatus.ONLINE.name()))._toQuery()))));
    }
}
