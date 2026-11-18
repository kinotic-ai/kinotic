package org.kinotic.grind;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.grind.api.model.TaskRecord;

import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    /**
     * The name of a job whose latest run cannot be read, as when the store does not answer; null
     * when every read succeeds.
     */
    public volatile String unreadableJob;

    public InMemoryJobRunRepository() {
        super(null, null, null, null);
    }

    @Override
    public synchronized Future<JobRun> saveRun(JobRun jobRun) {
        savedRuns.put(jobRun.getId(), jobRun);
        return completedOffThread(jobRun);
    }

    @Override
    public synchronized Future<Boolean> recordOutcome(String jobRunId, ExecutionStatus status, String error, Date finished, String source) {
        JobRun run = savedRuns.get(jobRunId);
        Future<Boolean> ret;
        if (run == null) {
            // the store fails a write to a record it does not hold
            ret = Future.failedFuture(new IllegalStateException("No run " + jobRunId + " is recorded"));
        } else {
            boolean executing = run.getStatus() == ExecutionStatus.RUNNING;
            if (executing) {
                run.setStatus(status).setError(error).setFinished(finished);
                run.getState().setDirty(true).setDirtyAt(System.currentTimeMillis());
            }
            ret = completedOffThread(executing);
        }
        return ret;
    }

    @Override
    public synchronized Future<Boolean> recordAbandoned(String jobRunId, StatusCondition condition, String source) {
        JobRun run = savedRuns.get(jobRunId);
        boolean executing = run.getStatus() == ExecutionStatus.RUNNING;
        if (executing) {
            run.setStatus(ExecutionStatus.FAILED).setError(condition.message()).setFinished(condition.since());
            run.getState().getConditions().add(condition);
            run.getState().setDirty(true).setDirtyAt(System.currentTimeMillis());
        }
        return completedOffThread(executing);
    }

    @Override
    public synchronized Future<Page<JobRun>> findRunningElsewhere(Set<String> members, Pageable pageable) {
        List<JobRun> elsewhere = savedRuns.values().stream()
                                          .filter(run -> run.getStatus() == ExecutionStatus.RUNNING)
                                          .filter(run -> !members.contains(run.getNodeId()))
                                          .toList();
        List<JobRun> firstPage = elsewhere.stream().limit(pageable.getPageSize()).toList();
        return completedOffThread(new Page<>(firstPage, (long) elsewhere.size()));
    }

    @Override
    public synchronized Future<TaskRecord> saveTask(TaskRecord taskRecord) {
        savedTasks.put(taskRecord.getId(), taskRecord);
        return completedOffThread(taskRecord);
    }

    @Override
    public synchronized Future<JobRun> findLatestRun(String name, String organizationId) {
        Future<JobRun> ret;
        if (name.equals(unreadableJob)) {
            ret = Future.failedFuture(new IllegalStateException("The runs of " + name + " cannot be read"));
        } else {
            List<JobRun> runs = savedRuns.values().stream()
                                         .filter(run -> name.equals(run.getName()) && Objects.equals(organizationId, run.getOrganizationId()))
                                         .sorted(Comparator.comparing(JobRun::getStarted))
                                         .toList();
            ret = completedOffThread(runs.isEmpty() ? null : runs.getLast());
        }
        return ret;
    }

    /**
     * The captured runs of the named job made on behalf of the system, ordered by when they started.
     */
    public synchronized List<JobRun> systemRunsOf(String name) {
        return savedRuns.values().stream()
                        .filter(run -> name.equals(run.getName()) && run.getOrganizationId() == null)
                        .sorted(Comparator.comparing(JobRun::getStarted))
                        .toList();
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
