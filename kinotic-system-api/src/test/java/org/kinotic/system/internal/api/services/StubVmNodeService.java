package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.workload.VmNode;
import org.kinotic.system.api.services.VmNodeService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory stand-in for the Elasticsearch backed {@link VmNodeService}.
 * {@link #findAvailableNode} always places on {@link #availableNode}.
 */
public class StubVmNodeService implements VmNodeService {

    public final Map<String, VmNode> saved = new LinkedHashMap<>();

    public VmNode availableNode;

    @Override
    public Future<VmNode> findAvailableNode(int requiredCpus, int requiredMemoryMb, int requiredDiskMb) {
        return Future.succeededFuture(availableNode);
    }

    @Override
    public Future<VmNode> save(VmNode entity) {
        saved.put(entity.getId(), entity);
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
        return Future.succeededFuture(saved.get(id));
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
        throw new UnsupportedOperationException();
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
