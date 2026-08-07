package org.kinotic.orchestrator.internal.api.grind;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.grind.JobRun;
import org.kinotic.domain.api.services.JobRunService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * In-memory stand-in for the Elasticsearch backed {@link JobRunService}, capturing what the
 * recorder writes.
 */
public class StubJobRunService implements JobRunService {

    public final Map<String, JobRun> saved = new LinkedHashMap<>();

    @Override
    public CompletableFuture<JobRun> save(JobRun entity) {
        saved.put(entity.getId(), entity);
        return CompletableFuture.completedFuture(entity);
    }

    @Override
    public CompletableFuture<JobRun> saveSync(JobRun entity) {
        return save(entity);
    }

    @Override
    public CompletableFuture<JobRun> create(JobRun entity) {
        return save(entity);
    }

    @Override
    public CompletableFuture<JobRun> createSync(JobRun entity) {
        return save(entity);
    }

    @Override
    public CompletableFuture<JobRun> findById(String id) {
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
    public CompletableFuture<Page<JobRun>> findAll(Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Page<JobRun>> search(String searchText, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Void> syncIndex() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Page<JobRun>> findByName(String name, Pageable pageable) {
        throw new UnsupportedOperationException();
    }
}
