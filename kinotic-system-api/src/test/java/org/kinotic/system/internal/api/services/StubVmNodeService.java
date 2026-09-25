package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.ReconcileState;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.StatusConditions;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.model.workload.VmNodeState;
import org.kinotic.system.api.services.VmNodeService;
import tools.jackson.databind.ObjectMapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * In-memory stand-in for the Elasticsearch backed {@link VmNodeService}.
 * {@link #findAvailableNode} always places on {@link #availableNode}. Records are stored and
 * returned as serialization round-trips the way Elasticsearch documents are, so a caller's
 * mutation of an entity after a save never alters the stored record, and a full save writes
 * back exactly what the caller's copy holds. The partial and state updates change only their
 * fields on the stored record, atomically per node, recompute the reconciled flag the way the
 * state scripts do, and fail when the node has no record, as the real updates do.
 */
public class StubVmNodeService implements VmNodeService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public final Map<String, VmNode> saved = new ConcurrentHashMap<>();

    public VmNode availableNode;

    @Override
    public Future<VmNode> findAvailableNode(double requiredCpus, int requiredMemoryMb, int requiredDiskMb) {
        return Future.succeededFuture(availableNode);
    }

    @Override
    public Future<Void> recordInventorySync(VmNode node) {
        saved.compute(node.getId(), (_, existing) -> {
            VmNode target = existing == null ? new VmNode(node.getId(), node.getName(), node.getHostname()) : existing;
            target.setName(node.getName())
                  .setHostname(node.getHostname())
                  .setProviderType(node.getProviderType())
                  .setTotalCpus(node.getTotalCpus())
                  .setTotalMemoryMb(node.getTotalMemoryMb())
                  .setTotalDiskMb(node.getTotalDiskMb())
                  .setFreeCpus(node.getFreeCpus())
                  .setFreeMemoryMb(node.getFreeMemoryMb())
                  .setFreeDiskMb(node.getFreeDiskMb())
                  .setWorkloadDataDir(node.getWorkloadDataDir())
                  .setLastSeen(new Date());
            return snapshot(target);
        });
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> recordHeartbeat(String nodeId, String healthMessage) {
        return update(nodeId, node -> node.setLastSeen(new Date()).setHealthMessage(healthMessage));
    }

    @Override
    public Future<VmNode> updateDesired(String nodeId, VmNodeState desired, String source) {
        return update(nodeId, node -> {
            ReconcileState<VmNodeState> state = node.getState();
            if (!desired.equals(state.getDesired())) {
                state.setDesired(desired).setGeneration(state.getGeneration() + 1);
                touched(state);
            }
        }).compose(v -> findById(nodeId));
    }

    @Override
    public Future<Void> reportObserved(String nodeId, VmNodeState observed, long seen, String source) {
        return update(nodeId, node -> {
            ReconcileState<VmNodeState> state = node.getState();
            if (!observed.equals(state.getObserved()) || state.getObservedGeneration() != seen) {
                state.setObserved(observed).setObservedGeneration(seen);
                touched(state);
            }
        });
    }

    @Override
    public Future<Boolean> setCondition(String nodeId, StatusCondition condition, String source) {
        boolean[] set = new boolean[1];
        return update(nodeId, node -> {
            set[0] = !StatusConditions.has(node.getState().getConditions(), condition.type());
            if (set[0]) {
                node.getState().getConditions().add(condition);
                touched(node.getState());
            }
        }).map(v -> set[0]);
    }

    @Override
    public Future<Boolean> clearCondition(String nodeId, StatusConditionType type, String source) {
        boolean[] cleared = new boolean[1];
        return update(nodeId, node -> {
            cleared[0] = node.getState().getConditions().removeIf(condition -> condition.type() == type);
            if (cleared[0]) {
                touched(node.getState());
            }
        }).map(v -> cleared[0]);
    }

    @Override
    public Future<Void> requestDeletion(String nodeId, String source) {
        return update(nodeId, node -> {
            if (node.getState().getDeletionRequested() == null) {
                node.getState().setDeletionRequested(new Date());
                touched(node.getState());
            }
        });
    }

    // What the state scripts' touched function does: marks the write and recomputes reconciled
    private static void touched(ReconcileState<VmNodeState> state) {
        state.setReconciled(Objects.equals(state.getDesired(), state.getObserved())
                                    && state.getGeneration() == state.getObservedGeneration()
                                    && state.getConditions().isEmpty()
                                    && state.getDeletionRequested() == null);
        state.setDirty(true).setDirtyAt(System.currentTimeMillis());
    }

    @Override
    public Future<Boolean> reserveSync(String nodeId, Workload workload) {
        boolean[] reserved = new boolean[1];
        // one node's reservations serialize under the map's lock, as the scripted update does on the shard
        return update(nodeId, node -> {
            reserved[0] = node.getFreeCpus() >= workload.getCpus()
                    && node.getFreeMemoryMb() >= workload.getMemoryMb()
                    && node.getFreeDiskMb() >= workload.getDiskSizeMb();
            if (reserved[0]) {
                node.setFreeCpus(node.getFreeCpus() - workload.getCpus())
                    .setFreeMemoryMb(node.getFreeMemoryMb() - workload.getMemoryMb())
                    .setFreeDiskMb(node.getFreeDiskMb() - workload.getDiskSizeMb());
            }
        }).map(v -> reserved[0]);
    }

    @Override
    public Future<Void> releaseSync(String nodeId, Workload workload) {
        return update(nodeId, node -> node.setFreeCpus(Math.min(node.getTotalCpus(), node.getFreeCpus() + workload.getCpus()))
                                          .setFreeMemoryMb(Math.min(node.getTotalMemoryMb(), node.getFreeMemoryMb() + workload.getMemoryMb()))
                                          .setFreeDiskMb(Math.min(node.getTotalDiskMb(), node.getFreeDiskMb() + workload.getDiskSizeMb())));
    }

    private Future<Void> update(String nodeId, Consumer<VmNode> partial) {
        // computeIfPresent holds the entry's lock while partial runs, and the re-put publishes the mutation
        VmNode stored = saved.computeIfPresent(nodeId, (_, node) -> {
            partial.accept(node);
            return node;
        });
        return stored == null
                ? Future.failedFuture(new IllegalStateException("No VmNode record for " + nodeId))
                : Future.succeededFuture();
    }

    @Override
    public Future<VmNode> save(VmNode entity) {
        saved.put(entity.getId(), snapshot(entity));
        return Future.succeededFuture(entity);
    }

    @Override
    public Future<VmNode> saveSync(VmNode entity) {
        return save(entity);
    }

    @Override
    public Future<VmNode> create(VmNode entity) {
        return save(entity);
    }

    @Override
    public Future<VmNode> createSync(VmNode entity) {
        return save(entity);
    }

    @Override
    public Future<VmNode> findById(String id) {
        VmNode stored = saved.get(id);
        return Future.succeededFuture(stored == null ? null : snapshot(stored));
    }

    private static VmNode snapshot(VmNode entity) {
        return MAPPER.readValue(MAPPER.writeValueAsBytes(entity), VmNode.class);
    }

    @Override
    public Future<Long> count() {
        return Future.succeededFuture((long) saved.size());
    }

    @Override
    public Future<Void> deleteById(String id) {
        saved.remove(id);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> deleteByIdSync(String id) {
        return deleteById(id);
    }

    @Override
    public Future<Page<VmNode>> findAll(Pageable pageable) {
        List<VmNode> all = saved.values().stream().map(StubVmNodeService::snapshot).toList();
        return Future.succeededFuture(new Page<>(all, (long) all.size()));
    }

    @Override
    public Future<Page<VmNode>> search(String searchText, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Void> syncIndex() {
        return Future.succeededFuture();
    }
}
