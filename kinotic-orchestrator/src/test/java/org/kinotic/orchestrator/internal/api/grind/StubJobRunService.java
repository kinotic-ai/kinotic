package org.kinotic.orchestrator.internal.api.grind;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.orchestrator.api.model.grind.JobOwner;
import org.kinotic.orchestrator.api.model.grind.JobRun;
import org.kinotic.orchestrator.api.services.JobRunService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory stand-in for the Elasticsearch backed {@link JobRunService}, capturing what the
 * recorder writes.
 */
public class StubJobRunService implements JobRunService {

    public final Map<String, JobRun> saved = new LinkedHashMap<>();

    @Override
    public Future<JobRun> save(JobRun entity) {
        saved.put(entity.getId(), entity);
        return Future.succeededFuture(entity);
    }

    @Override
    public Future<JobRun> saveSync(JobRun entity) {
        return save(entity);
    }

    @Override
    public Future<JobRun> create(JobRun entity) {
        return save(entity);
    }

    @Override
    public Future<JobRun> createSync(JobRun entity) {
        return save(entity);
    }

    @Override
    public Future<JobRun> findById(String id) {
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
    public Future<Page<JobRun>> findAll(Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Page<JobRun>> search(String searchText, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Void> syncIndex() {
        return Future.succeededFuture();
    }

    @Override
    public Future<Page<JobRun>> findByName(String name, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Page<JobRun>> findAllForOwner(JobOwner owner, Pageable pageable) {
        throw new UnsupportedOperationException();
    }
}
