package org.kinotic.grindv2.internal.api.services;

import org.kinotic.grindv2.internal.api.model.DefaultJobDefinition;
import org.kinotic.grindv2.internal.model.SerializedState;
import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.grindv2.api.model.ExecutionStatus;
import org.kinotic.grindv2.api.model.JobOwner;
import org.kinotic.grindv2.api.model.JobRun;
import org.kinotic.grindv2.api.repositories.JobRunRepository;
import org.kinotic.grindv2.api.model.StepRecord;
import org.kinotic.grindv2.api.model.StoreType;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Persists the {@link JobRun} and its {@link StepRecord}s as the run's lifecycle arrives
 * through the {@link RunListener} callbacks.
 */
@Slf4j
public class RunRecorder implements RunListener {

    private final String jobRunId;
    private final JobRun jobRun;
    private final JobRunRepository repository;

    private final Map<String, StepRecord> recordsByPath = new ConcurrentHashMap<>();
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
    public void stepsDiscovered(String parentPath, List<StepRecord> discovered, boolean dynamic) {
        if (dynamic) {
            StepRecord producer = recordsByPath.get(parentPath);
            if (producer != null) {
                producer.setDynamicSteps(true);
                enqueue(() -> repository.saveStep(producer));
            }
        }
        for (StepRecord record : discovered) {
            recordsByPath.put(record.getStepPath(), record);
            enqueue(() -> repository.saveStep(record));
        }
    }

    @Override
    public void stepStarted(String stepPath, String description) {
        StepRecord record = recordsByPath.computeIfAbsent(stepPath,
                                                          path -> new StepRecord().setId(jobRunId + ":" + path)
                                                                                  .setJobRunId(jobRunId)
                                                                                  .setStepPath(path));
        record.setDescription(description)
              .setStatus(ExecutionStatus.RUNNING)
              .setStarted(new Date());
        enqueue(() -> repository.saveStep(record));
    }

    @Override
    public void stepCompleted(String stepPath, StoreType storeType, String storedName,
                              Object storedValue, SerializedState serializedState) {
        StepRecord record = recordsByPath.get(stepPath);
        if (record == null) {
            log.warn("Step completed for unknown step path {} in run {}", stepPath, jobRunId);
        } else {
            record.setStatus(ExecutionStatus.COMPLETED)
                  .setFinished(new Date())
                  .setStoreType(storeType)
                  .setResultName(storedName);
            if (serializedState != null) {
                record.setResultValueType(serializedState.valueType())
                      .setResultValue(serializedState.value());
            }
            enqueue(() -> repository.saveStep(record));
        }
    }

    @Override
    public void stepFailed(String stepPath, Throwable error) {
        StepRecord record = recordsByPath.get(stepPath);
        if (record == null) {
            log.warn("Step failed for unknown step path {} in run {}", stepPath, jobRunId);
        } else {
            record.setStatus(ExecutionStatus.FAILED)
                  .setError(error.toString())
                  .setFinished(new Date());
            enqueue(() -> repository.saveStep(record));
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
     * terminates abnormally and in-flight steps will never report completion.
     */
    private void finishRemainingRecords(ExecutionStatus status) {
        for (StepRecord record : recordsByPath.values()) {
            if (record.getStatus() == ExecutionStatus.RUNNING) {
                record.setStatus(status)
                      .setFinished(new Date());
                enqueue(() -> repository.saveStep(record));
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
