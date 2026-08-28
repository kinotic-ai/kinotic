package org.kinotic.grind.internal.api.services;

import org.kinotic.grind.internal.api.model.DefaultJobDefinition;
import org.kinotic.grind.internal.model.SerializedState;
import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.StoreType;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Persists the {@link JobRun} and its {@link TaskRecord}s as the run's lifecycle arrives
 * through the {@link RunListener} callbacks.
 */
@Slf4j
public class RunRecorder implements RunListener {

    private final String jobRunId;
    private final JobRun jobRun;
    private final JobRunRepository repository;

    private final Map<String, TaskRecord> recordsByPath = new ConcurrentHashMap<>();
    // Persistence calls are chained so writes for the same document can never race each other
    private Future<Void> writeChain = Future.succeededFuture();

    public RunRecorder(String jobRunId,
                       DefaultJobDefinition definition,
                       JobOwner owner,
                       String resumedFrom,
                       JobRunRepository repository) {
        this.jobRunId = jobRunId;
        this.repository = repository;
        this.jobRun = new JobRun().setId(jobRunId)
                                  .setName(definition.getName())
                                  .setVersion(definition.getVersion())
                                  .setDescription(definition.getDescription())
                                  .setResumedFrom(resumedFrom);
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
        enqueue(() -> repository.saveRun(jobRun));
    }

    @Override
    public void tasksDiscovered(String parentPath, List<TaskRecord> discovered, boolean dynamic) {
        if (dynamic) {
            TaskRecord producer = recordsByPath.get(parentPath);
            if (producer != null) {
                producer.setDynamicTasks(true);
                enqueue(() -> repository.saveTask(producer));
            }
        }
        for (TaskRecord record : discovered) {
            recordsByPath.put(record.getTaskPath(), record);
            enqueue(() -> repository.saveTask(record));
        }
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
        enqueue(() -> repository.saveTask(record));
    }

    @Override
    public void taskProgress(String taskPath, int percentageComplete, String message) {
        // progress is transient stream data: persisting every tick would churn the write
        // chain, and a resumed or reloaded run has no use for a stale percentage
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
                  .setResultName(storedName);
            if (store.getType() == StoreType.STATE && serializedState != null) {
                record.setResultValueType(serializedState.valueType())
                      .setResultValue(serializedState.value());
            }
            if (store.isWire() && serializedState != null) {
                record.setWireValue(serializedState.value());
            }
            enqueue(() -> repository.saveTask(record));
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
            enqueue(() -> repository.saveTask(record));
        }
    }

    @Override
    public void runCompleted() {
        jobRun.setStatus(ExecutionStatus.COMPLETED)
              .setFinished(new Date());
        enqueue(() -> repository.saveRun(jobRun));
    }

    @Override
    public void runFailed(Throwable error) {
        finishRemainingRecords(ExecutionStatus.CANCELLED);
        jobRun.setStatus(ExecutionStatus.FAILED)
              .setError(error.toString())
              .setFinished(new Date());
        enqueue(() -> repository.saveRun(jobRun));
    }

    @Override
    public void runCancelled() {
        finishRemainingRecords(ExecutionStatus.CANCELLED);
        jobRun.setStatus(ExecutionStatus.CANCELLED)
              .setFinished(new Date());
        enqueue(() -> repository.saveRun(jobRun));
    }

    /**
     * Marks every record still RUNNING with the given terminal status. Used when the run
     * terminates abnormally and in-flight tasks will never report completion.
     */
    private void finishRemainingRecords(ExecutionStatus status) {
        for (TaskRecord record : recordsByPath.values()) {
            if (record.getStatus() == ExecutionStatus.RUNNING) {
                record.setStatus(status)
                      .setFinished(new Date());
                enqueue(() -> repository.saveTask(record));
            }
        }
    }

    private synchronized void enqueue(Supplier<Future<?>> writeOperation) {
        writeChain = writeChain.compose(v -> writeOperation.get()
                                                           .transform(ar -> {
                                                               if (ar.failed()) {
                                                                   log.warn("Failed to persist record for run {}", jobRunId, ar.cause());
                                                               }
                                                               return Future.succeededFuture();
                                                           }));
    }

}
