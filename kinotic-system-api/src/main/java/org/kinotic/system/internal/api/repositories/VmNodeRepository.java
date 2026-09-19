package org.kinotic.system.internal.api.repositories;

import org.kinotic.domain.internal.api.repositories.AbstractRepository;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.model.workload.VmNodeStatus;
import org.kinotic.system.api.model.workload.VmNodeStatusType;
import org.kinotic.system.api.model.workload.WorkloadReservation;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class VmNodeRepository extends AbstractRepository<VmNode> {

    // Shared by the three allocation scripts: finds the workload's entry in the node's reservations,
    // and keeps the fractional CPU total from drifting through repeated adds and subtracts
    private static final String ALLOCATION_FUNCTIONS = """
            Map held(def node, String workloadId) {
                if (node.reservations == null) {
                    node.reservations = new ArrayList();
                }
                for (def r : node.reservations) {
                    if (r.workloadId == workloadId) {
                        return r;
                    }
                }
                return null;
            }
            double cpus(double value) {
                return Math.round(value * 1000) / 1000.0;
            }
            """;

    // A workload already holding its room keeps it. The node declines with noop rather than going
    // negative, so the caller learns the capacity was taken.
    private static final String RESERVE_SCRIPT = ALLOCATION_FUNCTIONS + """
            def node = ctx._source;
            if (held(node, params.workloadId) == null) {
                if (node.availableCpus < params.cpus
                        || node.availableMemoryMb < params.memoryMb
                        || node.availableDiskMb < params.diskMb) {
                    ctx.op = 'noop';
                } else {
                    node.availableCpus = cpus(node.availableCpus - params.cpus);
                    node.availableMemoryMb -= params.memoryMb;
                    node.availableDiskMb -= params.diskMb;
                    node.reservations.add(['workloadId': params.workloadId, 'cpus': params.cpus,
                                           'memoryMb': params.memoryMb, 'diskMb': params.diskMb]);
                }
            }
            """;

    // Returns everything the workload holds and forgets it
    private static final String RELEASE_SCRIPT = ALLOCATION_FUNCTIONS + """
            def node = ctx._source;
            Map held = held(node, params.workloadId);
            if (held == null) {
                ctx.op = 'noop';
            } else {
                node.availableCpus = cpus(Math.min(node.totalCpus, node.availableCpus + held.cpus));
                node.availableMemoryMb = Math.min(node.totalMemoryMb, node.availableMemoryMb + held.memoryMb);
                node.availableDiskMb = Math.min(node.totalDiskMb, node.availableDiskMb + held.diskMb);
                node.reservations.remove(node.reservations.indexOf(held));
            }
            """;

    public VmNodeRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_vm_node", VmNode.class, crudServiceTemplate);
    }

    /**
     * Returns an {@link VmNodeStatusType#ONLINE} node with at least the requested resources
     * unallocated, or {@code null} when the cluster has no node with room for them.
     */
    public Future<VmNode> findAvailableNode(double requiredCpus, int requiredMemoryMb, int requiredDiskMb) {
        return findFirst(b -> b.query(composeFilter(termFilter("status.type", VmNodeStatusType.ONLINE.name()),
                                                    atLeast("availableCpus", requiredCpus),
                                                    atLeast("availableMemoryMb", requiredMemoryMb),
                                                    atLeast("availableDiskMb", requiredDiskMb))));
    }

    /**
     * Sets a node's status through a partial update touching only {@code status}, visible to search on
     * completion.
     */
    public Future<Void> updateStatusSync(String nodeId, VmNodeStatus status) {
        return crudServiceTemplate.partialUpdateSync(indexName, nodeId, Map.of("status", status), false);
    }

    /**
     * Takes a workload's room from a node's unallocated {@code available*} fields and records it in the
     * node's reservations, in one shard operation, so two reservations can never both be granted the
     * same capacity; visible to search on completion. A workload already holding its room keeps it.
     * @return true when the workload holds the room, false when the node does not have it
     */
    public Future<Boolean> reserveSync(String nodeId, WorkloadReservation reservation) {
        return crudServiceTemplate.scriptedUpdateSync(indexName, nodeId, RESERVE_SCRIPT,
                                                      Map.of("workloadId", reservation.getWorkloadId(),
                                                             "cpus", reservation.getCpus(),
                                                             "memoryMb", reservation.getMemoryMb(),
                                                             "diskMb", reservation.getDiskMb()));
    }

    /**
     * Returns everything a workload holds to a node's unallocated fields and drops its reservation, in one
     * shard operation, visible to search on completion. A workload holding nothing is left as it is.
     */
    public Future<Void> releaseSync(String nodeId, String workloadId) {
        return crudServiceTemplate.scriptedUpdateSync(indexName, nodeId, RELEASE_SCRIPT, Map.of("workloadId", workloadId))
                                  .mapEmpty();
    }

    private static Query atLeast(String field, double required) {
        return Query.of(q -> q.range(r -> r.number(n -> n.field(field).gte(required))));
    }
}
