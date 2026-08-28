package org.kinotic.grind;

import io.vertx.core.Future;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.grind.api.model.TaskRecord;

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
    public final Map<String, TaskRecord> savedTasks = new LinkedHashMap<>();

    @Override
    public synchronized Future<JobRun> saveRun(JobRun jobRun) {
        savedRuns.put(jobRun.getId(), jobRun);
        return Future.succeededFuture(jobRun);
    }

    @Override
    public synchronized Future<TaskRecord> saveTask(TaskRecord taskRecord) {
        savedTasks.put(taskRecord.getId(), taskRecord);
        return Future.succeededFuture(taskRecord);
    }

    @Override
    public synchronized Future<JobRun> findRun(String jobRunId) {
        return Future.succeededFuture(savedRuns.get(jobRunId));
    }

    @Override
    public synchronized Future<List<TaskRecord>> findTasks(String jobRunId) {
        return Future.succeededFuture(tasksOf(jobRunId));
    }

    /**
     * The captured records of the given run, ordered by task path.
     */
    public synchronized List<TaskRecord> tasksOf(String jobRunId) {
        return savedTasks.values().stream()
                         .filter(record -> jobRunId.equals(record.getJobRunId()))
                         .sorted(Comparator.comparing(TaskRecord::getTaskPath))
                         .toList();
    }

    /**
     * The captured record at the given path of the given run, or null.
     */
    public synchronized TaskRecord taskAt(String jobRunId, String taskPath) {
        return savedTasks.get(jobRunId + ":" + taskPath);
    }

}
