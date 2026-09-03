package org.kinotic.grind.internal.api.services;

import org.kinotic.grind.internal.api.model.DefaultJobDefinition;
import org.kinotic.grind.internal.model.SerializedState;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.internal.VertxInternal;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.StoreType;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Persists the {@link JobRun} and its {@link TaskRecord}s as the run's lifecycle arrives
 * through the {@link RunListener} callbacks. The ledger is written ahead of execution: every
 * callback returns only once its writes have been acknowledged by the store, so the run and
 * its PENDING records exist before the first task starts, a task's RUNNING record before its
 * body executes, and every terminal record before the run's event stream terminates.
 */
@Slf4j
public class RunRecorder implements RunListener {

    private final String jobRunId;
    private final JobRun jobRun;
    private final JobRunRepository repository;
    private final Context writeContext;

    private final Map<String, TaskRecord> recordsByPath = new ConcurrentHashMap<>();

    public RunRecorder(String jobRunId,
                       DefaultJobDefinition definition,
                       JobOwner owner,
                       String resumedFrom,
                       String nodeId,
                       JobRunRepository repository,
                       Vertx vertx) {
        this.jobRunId = jobRunId;
        this.repository = repository;
        // Writes are issued from an event-loop context of the recorder's own rather than the
        // run's virtual-thread context: the repository binds each write's completion to the
        // issuing context, and the run's context runs one task at a time, so a completion
        // bound to it would wait behind the executing task body - and behind the run thread
        // blocked in awaitWrite for that very completion
        this.writeContext = ((VertxInternal) vertx).createEventLoopContext();
        this.jobRun = new JobRun().setId(jobRunId)
                                  .setName(definition.getName())
                                  .setVersion(definition.getVersion())
                                  .setDescription(definition.getDescription())
                                  .setResumedFrom(resumedFrom)
                                  .setNodeId(nodeId);
        if (owner != null) {
            jobRun.setOrganizationId(owner.getOrganizationId())
                  .setApplicationId(owner.getApplicationId())
                  .setProjectId(owner.getProjectId());
        }
    }

    /**
     * Records the owner once it is known, for a resume where the owner comes from the
     * original run loaded after this recorder was created.
     */
    public void ownerResolved(String organizationId, String applicationId, String projectId) {
        jobRun.setOrganizationId(organizationId)
              .setApplicationId(applicationId)
              .setProjectId(projectId);
    }

    @Override
    public void runStarted() {
        jobRun.setStatus(ExecutionStatus.RUNNING)
              .setStarted(new Date());
        awaitWrite(this::saveRun);
    }

    @Override
    public void tasksDiscovered(String parentPath, List<TaskRecord> discovered, boolean dynamic) {
        List<TaskRecord> changed = new ArrayList<>(discovered);
        if (dynamic) {
            TaskRecord producer = recordsByPath.get(parentPath);
            if (producer != null) {
                producer.setDynamicTasks(true);
                changed.add(producer);
            }
        }
        for (TaskRecord record : discovered) {
            recordsByPath.put(record.getTaskPath(), record);
        }
        awaitWrite(() -> saveTasks(changed));
    }

    @Override
    public void taskStarted(String taskPath, String description) {
        TaskRecord record = recordsByPath.computeIfAbsent(taskPath,
                                                          path -> new TaskRecord().setId(jobRunId + ":" + path)
                                                                                  .setJobRunId(jobRunId)
                                                                                  .setTaskPath(path));
        record.setDescription(description)
              .setStatus(ExecutionStatus.RUNNING)
              .setStarted(new Date());
        awaitWrite(() -> saveTask(record));
    }

    @Override
    public void taskProgress(String taskPath, int percentageComplete, String message) {
        // progress is transient stream data: persisting every tick would cost a store round
        // trip per report, and a resumed or reloaded run has no use for a stale percentage
    }

    @Override
    public void taskCompleted(String taskPath, Store store, String storedName,
                              Object storedValue, SerializedState serializedState) {
        TaskRecord record = recordsByPath.get(taskPath);
        if (record == null) {
            log.warn("Task completed for unknown task path {} in run {}", taskPath, jobRunId);
        } else {
            record.setStatus(ExecutionStatus.COMPLETED)
                  .setFinished(new Date())
                  .setStoreType(store.getType())
                  .setStoredName(storedName);
            // wire publication rides the event stream only; the record persists just the
            // durable STATE the resume replays
            if (store.getType() == StoreType.STATE && serializedState != null) {
                record.setStateValueType(serializedState.valueType())
                      .setStateValue(serializedState.value());
            }
            awaitWrite(() -> saveTask(record));
        }
    }

    @Override
    public void taskFailed(String taskPath, Throwable error) {
        TaskRecord record = recordsByPath.get(taskPath);
        if (record == null) {
            log.warn("Task failed for unknown task path {} in run {}", taskPath, jobRunId);
        } else {
            record.setStatus(ExecutionStatus.FAILED)
                  .setError(error.toString())
                  .setFinished(new Date());
            awaitWrite(() -> saveTask(record));
        }
    }

    @Override
    public void runCompleted() {
        jobRun.setStatus(ExecutionStatus.COMPLETED)
              .setFinished(new Date());
        awaitWrite(this::saveRun);
    }

    @Override
    public void runFailed(Throwable error) {
        finishRemainingRecords(ExecutionStatus.CANCELLED);
        jobRun.setStatus(ExecutionStatus.FAILED)
              .setError(error.toString())
              .setFinished(new Date());
        awaitWrite(this::saveRun);
    }

    @Override
    public void runCancelled() {
        finishRemainingRecords(ExecutionStatus.CANCELLED);
        jobRun.setStatus(ExecutionStatus.CANCELLED)
              .setFinished(new Date());
        awaitWrite(this::saveRun);
    }

    /**
     * Marks every record still RUNNING with the given terminal status. Used when the run
     * terminates abnormally and in-flight tasks will never report completion.
     */
    private void finishRemainingRecords(ExecutionStatus status) {
        List<TaskRecord> remaining = new ArrayList<>();
        for (TaskRecord record : recordsByPath.values()) {
            if (record.getStatus() == ExecutionStatus.RUNNING) {
                record.setStatus(status)
                      .setFinished(new Date());
                remaining.add(record);
            }
        }
        if (!remaining.isEmpty()) {
            awaitWrite(() -> saveTasks(remaining));
        }
    }

    private Future<JobRun> saveRun() {
        return repository.saveRun(jobRun)
                         .onFailure(error -> log.warn("Failed to persist run {}", jobRunId, error));
    }

    private Future<TaskRecord> saveTask(TaskRecord record) {
        return repository.saveTask(record)
                         .onFailure(error -> log.warn("Failed to persist task record {} of run {}",
                                                      record.getTaskPath(), jobRunId, error));
    }

    private Future<?> saveTasks(List<TaskRecord> records) {
        // distinct documents, so the writes go out together and the wait is one round trip
        return Future.join(records.stream().map(this::saveTask).toList());
    }

    /**
     * Issues the write on the recorder's context and blocks the calling run thread until it
     * has been acknowledged, failed or not: a write that fails is logged by the issuer and the
     * run proceeds. The wait holds through cancellation of the run, so the terminal records of
     * a cancelled run still land before its stream terminates.
     */
    private void awaitWrite(Supplier<Future<?>> write) {
        CompletableFuture<Void> acknowledged = new CompletableFuture<>();
        // Future.future turns a write that throws while being issued into a failed future, so
        // the run thread below can never be left waiting on a write that was never sent
        writeContext.runOnContext(v -> Future.<Void>future(promise -> write.get().onComplete(ar -> promise.complete()))
                                             .onFailure(error -> log.warn("Failed to issue a ledger write for run {}",
                                                                          jobRunId, error))
                                             .onComplete(ar -> acknowledged.complete(null)));
        // join rather than get: an interrupt delivering cancellation must not abandon the write,
        // and join keeps the interrupt status set for the interpreter to act on
        acknowledged.join();
    }

}
