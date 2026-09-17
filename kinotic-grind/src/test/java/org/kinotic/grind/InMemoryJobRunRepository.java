package org.kinotic.grind;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.grind.api.model.TaskRecord;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * In-memory stand-in for the persistent {@link JobRunRepository}, capturing what the engine
 * writes so tests can assert on the ledger. Overrides every method the engine calls, so the
 * Elasticsearch template the superclass would use is never touched. Its futures behave like
 * the template's: completed from a foreign thread and bound to the calling Vert.x context.
 */
public class InMemoryJobRunRepository extends JobRunRepository {

    public final Map<String, JobRun> savedRuns = new LinkedHashMap<>();
    public final Map<String, TaskRecord> savedTasks = new LinkedHashMap<>();

    public InMemoryJobRunRepository() {
        super(null, null);
    }

    @Override
    public synchronized Future<JobRun> saveRun(JobRun jobRun) {
        savedRuns.put(jobRun.getId(), jobRun);
        return completedOffThread(jobRun);
    }

    @Override
    public synchronized Future<TaskRecord> saveTask(TaskRecord taskRecord) {
        savedTasks.put(taskRecord.getId(), taskRecord);
        return completedOffThread(taskRecord);
    }

    @Override
    public synchronized Future<JobRun> findRun(String jobRunId) {
        return completedOffThread(savedRuns.get(jobRunId));
    }

    @Override
    public synchronized Future<List<TaskRecord>> findTasks(String jobRunId) {
        return completedOffThread(tasksOf(jobRunId));
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

    /**
     * What the Elasticsearch-backed repository hands back: the client completes on a thread
     * of its own, and the future is bound to the context current at the call - so its
     * handlers are dispatched onto that context's task queue, which is the shape the
     * engine's recording has to work under.
     */
    private <T> Future<T> completedOffThread(T value) {
        CompletableFuture<T> completed = CompletableFuture.supplyAsync(() -> value);
        Context context = Vertx.currentContext();
        Future<T> ret;
        if (context != null) {
            ret = Future.fromCompletionStage(completed, context);
        } else {
            ret = Future.fromCompletionStage(completed);
        }
        return ret;
    }

}
