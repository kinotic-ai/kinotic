package org.kinotic.grindv2;

import io.vertx.core.Future;
import org.kinotic.grindv2.api.JobRun;
import org.kinotic.grindv2.api.JobRunRepository;
import org.kinotic.grindv2.api.StepRecord;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory stand-in for a persistent {@link JobRunRepository}, capturing what the engine
 * writes so tests can assert on the ledger.
 */
public class InMemoryJobRunRepository implements JobRunRepository {

    public final Map<String, JobRun> savedRuns = new LinkedHashMap<>();
    public final Map<String, StepRecord> savedSteps = new LinkedHashMap<>();

    @Override
    public synchronized Future<JobRun> saveRun(JobRun jobRun) {
        savedRuns.put(jobRun.getId(), jobRun);
        return Future.succeededFuture(jobRun);
    }

    @Override
    public synchronized Future<StepRecord> saveStep(StepRecord stepRecord) {
        savedSteps.put(stepRecord.getId(), stepRecord);
        return Future.succeededFuture(stepRecord);
    }

    @Override
    public synchronized Future<JobRun> findRun(String jobRunId) {
        return Future.succeededFuture(savedRuns.get(jobRunId));
    }

    @Override
    public synchronized Future<List<StepRecord>> findSteps(String jobRunId) {
        return Future.succeededFuture(stepsOf(jobRunId));
    }

    /**
     * The captured records of the given run, ordered by step path.
     */
    public synchronized List<StepRecord> stepsOf(String jobRunId) {
        return savedSteps.values().stream()
                         .filter(record -> jobRunId.equals(record.getJobRunId()))
                         .sorted(Comparator.comparing(StepRecord::getStepPath))
                         .toList();
    }

    /**
     * The captured record at the given path of the given run, or null.
     */
    public synchronized StepRecord stepAt(String jobRunId, String stepPath) {
        return savedSteps.get(jobRunId + ":" + stepPath);
    }

}
