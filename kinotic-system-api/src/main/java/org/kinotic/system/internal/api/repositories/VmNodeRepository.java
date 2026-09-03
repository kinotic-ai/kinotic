package org.kinotic.system.internal.api.repositories;

import org.kinotic.domain.internal.api.repositories.AbstractRepository;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.model.workload.VmNodeStatusType;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class VmNodeRepository extends AbstractRepository<VmNode> {

    public VmNodeRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_vm_node", VmNode.class, crudServiceTemplate);
    }

    /**
     * Returns an {@link VmNodeStatusType#ONLINE} node with at least the requested resources
     * unallocated, or {@code null} when the cluster has no node with room for them.
     */
    public Future<VmNode> findAvailableNode(int requiredCpus, int requiredMemoryMb, int requiredDiskMb) {
        return findFirst(b -> b.query(composeFilter(termFilter("status.type", VmNodeStatusType.ONLINE.name()),
                                                    atLeast("availableCpus", requiredCpus),
                                                    atLeast("availableMemoryMb", requiredMemoryMb),
                                                    atLeast("availableDiskMb", requiredDiskMb))));
    }

    private static Query atLeast(String field, int required) {
        return Query.of(q -> q.range(r -> r.number(n -> n.field(field).gte((double) required))));
    }
}
