package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.system.api.services.WorkloadService;
import tools.jackson.databind.ObjectMapper;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory stand-in for the Elasticsearch backed {@link WorkloadService}. Saves assign ids and
 * stamp {@code updated} like {@code DefaultWorkloadService.beforeSave} so status-report
 * timestamp guards behave as they do against the real service. Records are stored and returned
 * as serialization round-trips the way Elasticsearch documents are, so a caller's mutation of
 * an entity after a save never alters the stored record.
 */
public class StubWorkloadService implements WorkloadService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public final Map<String, Workload> saved = new LinkedHashMap<>();

    @Override
    public Future<Workload> save(Workload entity) {
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
        }
        entity.setUpdated(new Date());
        if (entity.getCreated() == null) {
            entity.setCreated(new Date());
        }
        saved.put(entity.getId(), snapshot(entity));
        return Future.succeededFuture(entity);
    }

    @Override
    public Future<Workload> saveSync(Workload entity) {
        return save(entity);
    }

    @Override
    public Future<Workload> create(Workload entity) {
        return save(entity);
    }

    @Override
    public Future<Workload> createSync(Workload entity) {
        return save(entity);
    }

    @Override
    public Future<Workload> findById(String id) {
        Workload stored = saved.get(id);
        return Future.succeededFuture(stored == null ? null : snapshot(stored));
    }

    private static Workload snapshot(Workload entity) {
        return MAPPER.readValue(MAPPER.writeValueAsBytes(entity), Workload.class);
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
    public Future<Page<Workload>> findAll(Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Page<Workload>> search(String searchText, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Void> syncIndex() {
        return Future.succeededFuture();
    }

    @Override
    public Future<Page<Workload>> findAllForNode(String nodeId, Pageable pageable) {
        List<Workload> matching = saved.values().stream()
                                       .filter(workload -> nodeId.equals(workload.getNodeId()))
                                       .toList();
        return Future.succeededFuture(new Page<>(matching, (long) matching.size()));
    }

    @Override
    public Future<Long> countForNode(String nodeId) {
        return findAllForNode(nodeId, null).map(Page::getTotalElements);
    }
}
