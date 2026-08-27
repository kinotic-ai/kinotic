package org.kinotic.grind.internal.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.OffsetPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.StepRecord;

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
    public final Map<String, StepRecord> savedStepRecords = new LinkedHashMap<>();

    /**
     * Returns the captured records belonging to the given run.
     */
    public List<StepRecord> forRun(String jobRunId) {
        return savedStepRecords.values().stream()
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
    public Future<StepRecord> saveStep(StepRecord stepRecord) {
        savedStepRecords.put(stepRecord.getId(), stepRecord);
        return Future.succeededFuture(stepRecord);
    }

    @Override
    public Future<Page<StepRecord>> findSteps(String jobRunId, Pageable pageable) {
        List<StepRecord> matching = forRun(jobRunId);
        int pageNumber = ((OffsetPageable) pageable).getPageNumber();
        int from = Math.min(pageNumber * pageable.getPageSize(), matching.size());
        int to = Math.min(from + pageable.getPageSize(), matching.size());
        return Future.succeededFuture(new Page<>(matching.subList(from, to), (long) matching.size()));
    }

}
