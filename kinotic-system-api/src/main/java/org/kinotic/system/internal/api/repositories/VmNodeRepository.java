package org.kinotic.system.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.repositories.ReconcilableRepository;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.repositories.ReconcileStateRepository;
import org.kinotic.domain.internal.api.repositories.WatchedDocument;
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

@Component
public class VmNodeRepository extends AbstractRepository<VmNode> implements ReconcilableRepository<VmNode> {

    public static final WatchedIndex WATCHED = new WatchedIndex(WatchedType.VM_NODE, "kinotic_vm_node");

    // Keeps the fractional CPU total from drifting through repeated subtracts and adds
    private static final String CPUS_FUNCTION = """
            double cpus(double value) {
                return Math.round(value * 1000) / 1000.0;
            }
            """;

    // The node declines with noop rather than going negative, so the caller learns the capacity was taken
    private static final String RESERVE_SCRIPT = CPUS_FUNCTION + """
            def node = ctx._source;
            if (node.freeCpus < params.cpus
                    || node.freeMemoryMb < params.memoryMb
                    || node.freeDiskMb < params.diskMb) {
                ctx.op = 'noop';
            } else {
                node.freeCpus = cpus(node.freeCpus - params.cpus);
                node.freeMemoryMb -= params.memoryMb;
                node.freeDiskMb -= params.diskMb;
            }
            """;

    // Clamped to the totals, so a return the node did not expect cannot make it look larger than it is
    private static final String RELEASE_SCRIPT = CPUS_FUNCTION + """
            def node = ctx._source;
            node.freeCpus = cpus(Math.min(node.totalCpus, node.freeCpus + params.cpus));
            node.freeMemoryMb = Math.min(node.totalMemoryMb, node.freeMemoryMb + params.memoryMb);
            node.freeDiskMb = Math.min(node.totalDiskMb, node.freeDiskMb + params.diskMb);
            """;

    private final WatchedStateRepository watchedStateRepository;
    private final ReconcileStateRepository reconcileStateRepository;

    public VmNodeRepository(CrudServiceTemplate crudServiceTemplate,
                            WatchedStateRepository watchedStateRepository,
                            ReconcileStateRepository reconcileStateRepository) {
        super(WATCHED.name(), VmNode.class, crudServiceTemplate);
        this.watchedStateRepository = watchedStateRepository;
        this.reconcileStateRepository = reconcileStateRepository;
    }

    @Override
    public WatchedType type() {
        return WATCHED.type();
    }

    @Override
    public String scopeOf(VmNode record) {
        return null;
    }

    @Override
    public Future<VmNode> find(String id, String scope) {
        return findById(id);
    }

    @Override
    public Future<Page<VmNode>> findDirty(Pageable pageable) {
        return watchedStateRepository.findDirty(indexName, type, pageable);
    }

    @Override
    public Future<Void> clearDirty(String id, String scope, long dirtyAt) {
        return watchedStateRepository.clearDirty(document(id), dirtyAt);
    }

    @Override
    public Future<Page<VmNode>> findUnreconciled(Pageable pageable) {
        return reconcileStateRepository.findUnreconciled(indexName, type, pageable);
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
     * Writes what a node reports at registration and the free capacity rebuilt from its workload
     * records — name, hostname, provider, totals, free capacity, data directory — and stamps
     * {@code lastSeen}, creating the record for a node registering for the first time. The node's
     * state and health message are left as they are. Visible to search on completion.
     */
    public Future<Void> recordInventorySync(VmNode node) {
        Validate.notNull(node, "node cannot be null");
        Validate.notBlank(node.getId(), "node id cannot be blank");
        Map<String, Object> inventory = new HashMap<>();
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
        return crudServiceTemplate.partialUpdateSync(indexName, node.getId(), inventory, true);
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
     * Writes what the node should be and enters the change in the ledger; visible to search on
     * completion. Fails for a node that is not registered.
     *
     * @param nodeId  the node
     * @param desired what the node should be
     * @param source  what caused it, for the ledger
     * @return the record as it stands once the intent is in place
     */
    public Future<VmNode> updateDesired(String nodeId, VmNodeState desired, String source) {
        Validate.notBlank(nodeId, "nodeId cannot be blank");
        return reconcileStateRepository.updateDesired(document(nodeId), desired, null, source)
                                       .compose(v -> findById(nodeId));
    }

    /**
     * Writes what the node reports it is and which generation of intent that answers, and enters
     * the change in the ledger; visible to search on completion.
     *
     * @param nodeId   the node
     * @param observed what the node is
     * @param seen     the generation of intent the report answers
     * @param source   what caused it, for the ledger
     */
    public Future<Void> reportObserved(String nodeId, VmNodeState observed, long seen, String source) {
        Validate.notBlank(nodeId, "nodeId cannot be blank");
        return reconcileStateRepository.reportObserved(document(nodeId), observed, seen, source).mapEmpty();
    }

    /**
     * @see WatchedStateRepository#setCondition(WatchedDocument, StatusCondition, String)
     */
    public Future<Boolean> setCondition(String nodeId, StatusCondition condition, String source) {
        return watchedStateRepository.setCondition(document(nodeId), condition, source);
    }

    /**
     * @see WatchedStateRepository#clearCondition(WatchedDocument, StatusConditionType, String)
     */
    public Future<Boolean> clearCondition(String nodeId, StatusConditionType type, String source) {
        return watchedStateRepository.clearCondition(document(nodeId), type, source);
    }

    /**
     * @see ReconcileStateRepository#requestDeletion(WatchedDocument, String)
     */
    public Future<Void> requestDeletion(String nodeId, String source) {
        return reconcileStateRepository.requestDeletion(document(nodeId), source).mapEmpty();
    }

    /**
     * Takes a workload's room, the CPU, memory and disk it is sized for, from a node's {@code free*}
     * fields in one shard operation, so two reservations can never both be granted the same capacity;
     * visible to search on completion.
     * @return true when the room is the workload's, false when the node does not have it
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

    private static WatchedDocument document(String nodeId) {
        return WatchedDocument.of(WATCHED, nodeId);
    }

    private static Query atLeast(String field, double required) {
        return Query.of(q -> q.range(r -> r.number(n -> n.field(field).gte(required))));
    }
}
