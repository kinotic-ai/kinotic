package org.kinotic.os.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.os.api.model.workload.VmNode;
import org.kinotic.os.api.model.workload.VmNodeStatus;
import org.kinotic.os.api.services.VmNodeService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultVmNodeService extends AbstractCrudService<VmNode> implements VmNodeService {

    public DefaultVmNodeService(ElasticsearchAsyncClient esAsyncClient,
                                CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_vm_node",
              VmNode.class,
              esAsyncClient,
              crudServiceTemplate);
    }

    @Override
    public CompletableFuture<VmNode> findAvailableNode(int requiredCpus, int requiredMemoryMb, int requiredDiskMb) {
        // Use a script query to compute available resources at query time.
        // Available = total - allocated, and we need available >= required.
        // For simplicity, we use range queries on the allocated fields combined with the totals.
        // However, ES doesn't support computed fields in queries directly.
        // Instead, we search for ONLINE nodes and then filter in code.
        return crudServiceTemplate.search(indexName,
                                          Pageable.create(0, 100, null),
                                          type,
                                          builder -> builder
                .query(q -> q
                        .bool(b -> b
                                .filter(TermQuery.of(tq -> tq.field("status").value(VmNodeStatus.ONLINE.name()))._toQuery())
                        )))
                .thenApply(page -> page.getContent()
                                       .stream()
                                       .filter(node -> node.getAvailableCpus() >= requiredCpus
                                               && node.getAvailableMemoryMb() >= requiredMemoryMb
                                               && node.getAvailableDiskMb() >= requiredDiskMb)
                                       .findFirst()
                                       .orElse(null));
    }

    @Override
    public CompletableFuture<VmNode> save(VmNode entity) {
        Validate.notNull(entity, "VmNode cannot be null");
        Validate.notNull(entity.getId(), "VmNode id cannot be null");
        entity.setLastSeen(new Date());
        return super.save(entity);
    }

}
