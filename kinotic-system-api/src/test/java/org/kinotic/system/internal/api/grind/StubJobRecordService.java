package org.kinotic.system.internal.api.grind;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.OffsetPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.management.api.model.grind.JobOwner;
import org.kinotic.management.api.model.grind.JobRun;
import org.kinotic.management.api.model.grind.TaskRecord;
import org.kinotic.management.api.services.JobRecordService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory stand-in for the Elasticsearch backed {@link JobRecordService}, capturing what
 * the recorder writes. {@link #findTaskRecordsForJobRun} pages like the real repository so
 * the replay ledger loader can be exercised against it.
 */
public class StubJobRecordService implements JobRecordService {

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
    public Future<JobRun> saveJobRun(JobRun jobRun) {
        savedJobRuns.put(jobRun.getId(), jobRun);
        return Future.succeededFuture(jobRun);
    }

    @Override
    public Future<JobRun> findJobRunById(String jobRunId) {
        return Future.succeededFuture(savedJobRuns.get(jobRunId));
    }

    @Override
    public Future<Page<JobRun>> findAllJobRuns(Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Page<JobRun>> findJobRunsForOwner(JobOwner owner, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<TaskRecord> saveTaskRecord(TaskRecord taskRecord) {
        savedTaskRecords.put(taskRecord.getId(), taskRecord);
        return Future.succeededFuture(taskRecord);
    }

    @Override
    public Future<Page<TaskRecord>> findTaskRecordsForJobRun(String jobRunId, Pageable pageable) {
        List<TaskRecord> matching = forRun(jobRunId);
        int pageNumber = ((OffsetPageable) pageable).getPageNumber();
        int from = Math.min(pageNumber * pageable.getPageSize(), matching.size());
        int to = Math.min(from + pageable.getPageSize(), matching.size());
        return Future.succeededFuture(new Page<>(matching.subList(from, to), (long) matching.size()));
    }

}
