package org.kinotic.orchestrator.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.orchestrator.api.model.workload.VmNode;

import java.util.concurrent.CompletableFuture;

/**
 * Service for managing {@link VmNode} entities.
 * Tracks available nodes in the cluster that can host workloads.
 */
@Publish
@Zone(DomainUtil.SYSTEM_ZONE)
public interface VmNodeService extends IdentifiableCrudService<VmNode, String> {

    /**
     * Finds a node with sufficient resources to host a workload with the given requirements.
     * @param requiredCpus the number of vCPUs required
     * @param requiredMemoryMb the amount of memory required in megabytes
     * @param requiredDiskMb the amount of disk space required in megabytes
     * @return a future that will complete with a suitable node, or null if none available
     */
    CompletableFuture<VmNode> findAvailableNode(int requiredCpus, int requiredMemoryMb, int requiredDiskMb);

}
