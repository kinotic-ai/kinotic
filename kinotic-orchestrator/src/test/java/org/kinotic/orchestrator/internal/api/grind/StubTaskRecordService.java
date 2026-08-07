package org.kinotic.orchestrator.internal.api.grind;

import org.kinotic.core.api.crud.OffsetPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.orchestrator.api.model.grind.TaskRecord;
import org.kinotic.orchestrator.api.services.TaskRecordService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
    public CompletableFuture<TaskRecord> save(TaskRecord entity) {
        saved.put(entity.getId(), entity);
        return CompletableFuture.completedFuture(entity);
    }

    @Override
    public CompletableFuture<TaskRecord> saveSync(TaskRecord entity) {
        return save(entity);
    }

    @Override
    public CompletableFuture<TaskRecord> create(TaskRecord entity) {
        return save(entity);
    }

    @Override
    public CompletableFuture<TaskRecord> createSync(TaskRecord entity) {
        return save(entity);
    }

    @Override
    public CompletableFuture<TaskRecord> findById(String id) {
        return CompletableFuture.completedFuture(saved.get(id));
    }

    @Override
    public CompletableFuture<Long> count() {
        return CompletableFuture.completedFuture((long) saved.size());
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        saved.remove(id);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteByIdSync(String id) {
        return deleteById(id);
    }

    @Override
    public CompletableFuture<Page<TaskRecord>> findAll(Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Page<TaskRecord>> search(String searchText, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Void> syncIndex() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Page<TaskRecord>> findAllForJobRun(String jobRunId, Pageable pageable) {
        List<TaskRecord> matching = forRun(jobRunId);
        int pageNumber = ((OffsetPageable) pageable).getPageNumber();
        int from = Math.min(pageNumber * pageable.getPageSize(), matching.size());
        int to = Math.min(from + pageable.getPageSize(), matching.size());
        return CompletableFuture.completedFuture(new Page<>(matching.subList(from, to), (long) matching.size()));
    }
}
