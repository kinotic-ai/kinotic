package org.kinotic.system.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.internal.api.repositories.AbstractReconcilableRepository;
import org.kinotic.domain.internal.api.repositories.ReconcileStateRepository;
import org.kinotic.domain.internal.api.repositories.WatchEventRepository;
import org.kinotic.domain.internal.api.repositories.WatchedIndex;
import org.kinotic.domain.internal.api.repositories.WatchedStateRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.model.workload.VmNodeState;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class VmNodeRepository extends AbstractReconcilableRepository<VmNode, VmNodeState> {

    private static final WatchedIndex WATCHED = new WatchedIndex(WatchedType.VM_NODE, "kinotic_vm_node");

    // Keeps the fractional CPU total from drifting through repeated subtracts and adds
    private static final String CPUS_FUNCTION = """
            double cpus(double value) {
                return Math.round(value * 1000) / 1000.0;
            }
            """;

    // The node declines with noop rather than going negative or taking a run while not in its desired
    // state, so the caller learns the room was taken, or the node left that state, since it was picked
    private static final String RESERVE_SCRIPT = CPUS_FUNCTION + """
            def node = ctx._source;
            if (node.state?.reconciled != true
                    || node.freeCpus < params.cpus
                    || node.freeMemoryMb < params.memoryMb
                    || node.freeDiskMb < params.diskMb) {
                ctx.op = 'noop';
            } else {
                node.freeCpus = cpus(node.freeCpus - params.cpus);
                node.freeMemoryMb -= params.memoryMb;
                node.freeDiskMb -= params.diskMb;
            }
            """;

    // A mark on a node is inferred from what was read of it, and a heartbeat since that read is the
    // node's own word against the inference: the script declines when lastSeen moved, so the
    // heartbeat, whose read found no mark to clear, and this write cannot leave a node just heard marked
    private static final String SET_CONDITION_UNLESS_HEARD = WatchedStateRepository.STATE_FUNCTIONS + """
            if (ctx._source.lastSeen != params.lastSeen) {
                ctx.op = 'noop';
            } else {
            """ + WatchedStateRepository.ADD_CONDITION + """
            }
            """;

    // The free capacity is rebuilt from a read of the node's runs, and a reservation or a release
    // between that read and this write moved the counters the rebuild overwrites: the script declines
    // when they differ from what the caller read, so the rebuild runs again on what is there. A node
    // registering for the first time is created from the same fields.
    private static final String RECORD_INVENTORY = """
            def node = ctx._source;
            if (params.expected != null
                    && (node.freeCpus != params.expected.freeCpus
                        || node.freeMemoryMb != params.expected.freeMemoryMb
                        || node.freeDiskMb != params.expected.freeDiskMb)) {
                ctx.op = 'noop';
            } else {
                for (def field : params.inventory.entrySet()) {
                    node[field.getKey()] = field.getValue();
                }
            }
            """;

    // Clamped to the totals, so a return the node did not expect cannot make it look larger than it is
    private static final String RELEASE_SCRIPT = CPUS_FUNCTION + """
            def node = ctx._source;
            node.freeCpus = cpus(Math.min(node.totalCpus, node.freeCpus + params.cpus));
            node.freeMemoryMb = Math.min(node.totalMemoryMb, node.freeMemoryMb + params.memoryMb);
            node.freeDiskMb = Math.min(node.totalDiskMb, node.freeDiskMb + params.diskMb);
            """;

    public VmNodeRepository(CrudServiceTemplate crudServiceTemplate,
                            WatchedStateRepository watchedStateRepository,
                            WatchEventRepository watchEventRepository,
                            ReconcileStateRepository reconcileStateRepository) {
        super(WATCHED, VmNode.class, crudServiceTemplate, watchedStateRepository, watchEventRepository, reconcileStateRepository);
    }

    // A node belongs to the platform, not to an organization
    @Override
    public String scopeOf(VmNode record) {
        return null;
    }

    /**
     * Returns a node in its desired state — taking workloads, reachable, and not being deregistered —
     * with at least the requested resources unallocated, or {@code null} when the cluster has no such
     * node with room for them.
     */
    public Future<VmNode> findAvailableNode(double requiredCpus, int requiredMemoryMb, int requiredDiskMb) {
        return findFirst(b -> b.query(composeFilter(termFilter("state.reconciled", true),
                                                    atLeast("freeCpus", requiredCpus),
                                                    atLeast("freeMemoryMb", requiredMemoryMb),
                                                    atLeast("freeDiskMb", requiredDiskMb))));
    }

    /**
     * Sets the condition on the node as it was read and records it; a node heard since that read, or
     * one already carrying a condition of the type, is left as it is. Visible to search on completion.
     *
     * @param node      the node as read, whose {@code lastSeen} the write is conditional on
     * @param condition the condition to set
     * @param source    what caused it, for the ledger
     * @return true when the condition was set
     */
    public Future<Boolean> setCondition(VmNode node, StatusCondition condition, String source) {
        Validate.notNull(node, "node cannot be null");
        Map<String, Object> params = new HashMap<>();
        // the same mapper serializes the date the heartbeat wrote and this one, so the two compare as
        // written; a node never heard has neither
        if (node.getLastSeen() != null) {
            params.put("lastSeen", node.getLastSeen());
        }
        return watchedStateRepository.setCondition(document(node.getId()), condition, source, SET_CONDITION_UNLESS_HEARD, params);
    }

    /**
     * Writes what a node reports at registration and the free capacity rebuilt from its workload
     * records — name, hostname, provider, totals, free capacity, data directory — and stamps
     * {@code lastSeen}, creating the record for a node registering for the first time. The node's
     * state and health message are left as they are. Visible to search on completion.
     *
     * @param node   what the node reports, with the free capacity rebuilt from its runs
     * @param asRead the node's record as read before the rebuild, or null for a node registering for
     *               the first time
     * @return true when the inventory was written, false when the node's free capacity moved since
     * the read it was rebuilt from
     */
    public Future<Boolean> recordInventorySync(VmNode node, VmNode asRead) {
        Validate.notNull(node, "node cannot be null");
        Validate.notBlank(node.getId(), "node id cannot be blank");
        Map<String, Object> inventory = new HashMap<>();
        // the upsert creates the document from this map alone, and a read returns _source as it is
        inventory.put("id", node.getId());
        inventory.put("name", node.getName());
        inventory.put("hostname", node.getHostname());
        inventory.put("providerType", node.getProviderType());
        inventory.put("totalCpus", node.getTotalCpus());
        inventory.put("totalMemoryMb", node.getTotalMemoryMb());
        inventory.put("totalDiskMb", node.getTotalDiskMb());
        inventory.put("freeCpus", node.getFreeCpus());
        inventory.put("freeMemoryMb", node.getFreeMemoryMb());
        inventory.put("freeDiskMb", node.getFreeDiskMb());
        inventory.put("workloadDataDir", node.getWorkloadDataDir());
        inventory.put("lastSeen", new Date());
        Map<String, Object> params = new HashMap<>();
        params.put("inventory", inventory);
        if (asRead != null) {
            params.put("expected", Map.of("freeCpus", asRead.getFreeCpus(),
                                          "freeMemoryMb", asRead.getFreeMemoryMb(),
                                          "freeDiskMb", asRead.getFreeDiskMb()));
        }
        return crudServiceTemplate.scriptedUpdateReturningSourceSync(indexName, node.getId(), RECORD_INVENTORY, params,
                                                                     u -> u.upsert(inventory).scriptedUpsert(true))
                                  .map(Objects::nonNull);
    }

    /**
     * Stamps {@code lastSeen} and writes the reason the node gives for not taking workloads, null when
     * it gives none, leaving every other field as it is.
     */
    public Future<Void> recordHeartbeat(String nodeId, String healthMessage) {
        Validate.notBlank(nodeId, "nodeId cannot be blank");
        Map<String, Object> heartbeat = new HashMap<>();
        heartbeat.put("lastSeen", new Date());
        heartbeat.put("healthMessage", healthMessage);
        return crudServiceTemplate.partialUpdate(indexName, nodeId, heartbeat, false);
    }

    /**
     * Takes a workload's room, the CPU, memory and disk it is sized for, from a node's {@code free*}
     * fields while the node is in its desired state, in one shard operation, so two reservations can
     * never both be granted the same capacity and a node that stopped taking workloads since it was
     * picked grants none; visible to search on completion.
     * @return true when the room is the workload's, false when the node does not have it or is not
     * taking workloads
     */
    public Future<Boolean> reserveSync(String nodeId, Workload workload) {
        return crudServiceTemplate.scriptedUpdateSync(indexName, nodeId, RESERVE_SCRIPT, room(workload));
    }

    /**
     * Returns a workload's room to a node's {@code free*} fields in one shard operation, visible to
     * search on completion.
     */
    public Future<Void> releaseSync(String nodeId, Workload workload) {
        return crudServiceTemplate.scriptedUpdateSync(indexName, nodeId, RELEASE_SCRIPT, room(workload))
                                  .mapEmpty();
    }

    private static Map<String, Object> room(Workload workload) {
        return Map.of("cpus", workload.getCpus(), "memoryMb", workload.getMemoryMb(), "diskMb", workload.getDiskSizeMb());
    }

    private static Query atLeast(String field, double required) {
        return Query.of(q -> q.range(r -> r.number(n -> n.field(field).gte(required))));
    }
}
