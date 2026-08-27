package org.kinotic.grind.internal.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.OffsetPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.TaskRecord;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory stand-in for the Elasticsearch backed {@link JobRunService}, capturing what
 * the recorder writes. {@link #findSteps} pages like the real repository so
 * the replay ledger loader can be exercised against it.
 */
public class StubJobRunService implements JobRunService {

    public final Map<String, JobRun> savedJobRuns = new LinkedHashMap<>();
    public final Map<String, TaskRecord> savedTaskRecords = new LinkedHashMap<>();

    /**
     * Returns the captured records belonging to the given run.
     */
    public List<TaskRecord> forRun(String jobRunId) {
        return savedTaskRecords.values().stream()
                               .filter(record -> jobRunId.equals(record.getJobRunId()))
                               .toList();
    }

    @Override
    public Future<JobRun> save(JobRun jobRun) {
        savedJobRuns.put(jobRun.getId(), jobRun);
        return Future.succeededFuture(jobRun);
    }

    @Override
    public Future<JobRun> findById(String jobRunId) {
        return Future.succeededFuture(savedJobRuns.get(jobRunId));
    }

    @Override
    public Future<Page<JobRun>> findAll(Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Page<JobRun>> findAllForOwner(JobOwner owner, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<TaskRecord> saveStep(TaskRecord taskRecord) {
        savedTaskRecords.put(taskRecord.getId(), taskRecord);
        return Future.succeededFuture(taskRecord);
    }

    @Override
    public Future<Page<TaskRecord>> findSteps(String jobRunId, Pageable pageable) {
        List<TaskRecord> matching = forRun(jobRunId);
        int pageNumber = ((OffsetPageable) pageable).getPageNumber();
        int from = Math.min(pageNumber * pageable.getPageSize(), matching.size());
        int to = Math.min(from + pageable.getPageSize(), matching.size());
        return Future.succeededFuture(new Page<>(matching.subList(from, to), (long) matching.size()));
    }

}
