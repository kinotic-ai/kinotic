package org.kinotic.orchestrator.internal.api.grind;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.OffsetPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.orchestrator.api.model.grind.TaskRecord;
import org.kinotic.orchestrator.api.services.TaskRecordService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory stand-in for the Elasticsearch backed {@link TaskRecordService}, capturing what the
 * recorder writes. {@link #findAllForJobRun} pages like the real repository so the replay ledger
 * loader can be exercised against it.
 */
public class StubTaskRecordService implements TaskRecordService {

    public final Map<String, TaskRecord> saved = new LinkedHashMap<>();

    /**
     * Returns the captured records belonging to the given run.
     */
    public List<TaskRecord> forRun(String jobRunId) {
        return saved.values().stream()
                    .filter(record -> jobRunId.equals(record.getJobRunId()))
                    .toList();
    }

    @Override
    public Future<TaskRecord> save(TaskRecord entity) {
        saved.put(entity.getId(), entity);
        return Future.succeededFuture(entity);
    }

    @Override
    public Future<TaskRecord> saveSync(TaskRecord entity) {
        return save(entity);
    }

    @Override
    public Future<TaskRecord> create(TaskRecord entity) {
        return save(entity);
    }

    @Override
    public Future<TaskRecord> createSync(TaskRecord entity) {
        return save(entity);
    }

    @Override
    public Future<TaskRecord> findById(String id) {
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
    public Future<Page<TaskRecord>> findAll(Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Page<TaskRecord>> search(String searchText, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Void> syncIndex() {
        return Future.succeededFuture();
    }

    @Override
    public Future<Page<TaskRecord>> findAllForJobRun(String jobRunId, Pageable pageable) {
        List<TaskRecord> matching = forRun(jobRunId);
        int pageNumber = ((OffsetPageable) pageable).getPageNumber();
        int from = Math.min(pageNumber * pageable.getPageSize(), matching.size());
        int to = Math.min(from + pageable.getPageSize(), matching.size());
        return Future.succeededFuture(new Page<>(matching.subList(from, to), (long) matching.size()));
    }
}
