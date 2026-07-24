package org.kinotic.domain.internal.api.repositories;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.workload.VmNode;
import org.kinotic.domain.api.model.workload.VmNodeStatus;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class VmNodeRepository extends AbstractRepository<VmNode> {

    public VmNodeRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_vm_node", VmNode.class, crudServiceTemplate);
    }

    /**
     * Returns the first page (up to 100) of nodes whose {@code status} is {@link VmNodeStatus#ONLINE}.
     * Resource-availability filtering is left to the caller because Elasticsearch can't express the
     * "available = total - allocated" computation as a server-side query.
     */
    public CompletableFuture<Page<VmNode>> findOnlineNodes() {
        return findAll(Pageable.ofSize(100), b -> b.query(termFilter("status", VmNodeStatus.ONLINE.name())));
    }
}
