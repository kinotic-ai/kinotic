package org.kinotic.grind.internal.api.services;

import org.kinotic.grind.internal.api.model.DefaultJobDefinition;
import org.kinotic.grind.internal.model.SerializedState;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.kinotic.domain.api.model.WatchedParent;
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

import tools.jackson.databind.JsonNode;

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
 * body executes, and every terminal record before the run's event stream terminates. A run whose
 * record cannot be written is not started: {@link #runStarted()} fails it. A run's outcome that
 * cannot be written is written again until it is.
 */
@Slf4j
public class RunRecorder implements RunListener {

    // How many attempts at writing the run's outcome the run waits for, how long after the first
    // failure the write is issued again, and the longest two attempts are apart
    private static final int OUTCOME_ATTEMPTS_AWAITED = 3;
    private static final long OUTCOME_RETRY_MS = 1_000;
    private static final long OUTCOME_RETRY_MAX_MS = 300_000;

    private final String jobRunId;
    private final JobRun jobRun;
    private final JobRunRepository repository;
    private final Context writeContext;

    private final Map<String, TaskRecord> recordsByPath = new ConcurrentHashMap<>();

    // Whether the run's record was written, which a run that fails before or at its start has none of
    private boolean runRecorded;

    public RunRecorder(String jobRunId,
                       DefaultJobDefinition definition,
                       JobOwner owner,
                       WatchedParent parent,
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
        jobRun.getState().setParent(parent);
        if (owner != null) {
            jobRun.setOrganizationId(owner.getOrganizationId())
                  .setApplicationId(owner.getApplicationId())
                  .setProjectId(owner.getProjectId());
        }
    }

    /**
     * Records the owner and what the run was made by once they are known, for a resume where
     * both come from the original run loaded after this recorder was created.
     */
    public void ownerResolved(String organizationId, String applicationId, String projectId, WatchedParent parent) {
        jobRun.setOrganizationId(organizationId)
              .setApplicationId(applicationId)
              .setProjectId(projectId);
        jobRun.getState().setParent(parent);
    }

    @Override
    public void runStarted() {
        jobRun.setStatus(ExecutionStatus.RUNNING)
              .setStarted(new Date());
        Throwable failure = awaitWrite(this::saveRun);
        if (failure != null) {
            throw new IllegalStateException("Run " + jobRunId + " of job " + jobRun.getName()
                    + " was not started, since its record could not be written", failure);
        }
        runRecorded = true;
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
    public void taskStarted(String taskPath, String description, JsonNode view) {
        TaskRecord record = recordsByPath.computeIfAbsent(taskPath,
                                                          path -> new TaskRecord().setId(jobRunId + ":" + path)
                                                                                  .setJobRunId(jobRunId)
                                                                                  .setTaskPath(path));
        record.setDescription(description)
              .setStatus(ExecutionStatus.RUNNING)
              .setStarted(new Date());
        if (view != null) {
            record.setView(view);
        }
        awaitWrite(() -> saveTask(record));
    }

    @Override
    public void taskProgress(String taskPath, int percentageComplete, String message) {
        // progress is transient stream data: persisting every tick would cost a store round
        // trip per report, and a resumed or reloaded run has no use for a stale percentage
    }

    @Override
    public void taskViewed(String taskPath, JsonNode view) {
        TaskRecord record = recordsByPath.get(taskPath);
        if (record == null) {
            log.warn("Task view for unknown task path {} in run {}", taskPath, jobRunId);
        } else {
            // written through, unlike progress: a view changes rarely, and a run whose node leaves
            // mid-task still shows the view it was showing
            record.setView(view);
            awaitWrite(() -> saveTask(record));
        }
    }

    @Override
    public void taskCompleted(String taskPath, Store store, String storedName,
                              Object storedValue, SerializedState serializedState, JsonNode view) {
        TaskRecord record = recordsByPath.get(taskPath);
        if (record == null) {
            log.warn("Task completed for unknown task path {} in run {}", taskPath, jobRunId);
        } else {
            record.setStatus(ExecutionStatus.COMPLETED)
                  .setFinished(new Date())
                  .setStoreType(store.getType())
                  .setStoredName(storedName);
            // the last view is written with the completion, one write where reporting it first would take two
            if (view != null) {
                record.setView(view);
            }
            // the resume replays the durable STATE; watchers read the wired value, so a finished
            // run renders what the live stream showed
            if (store.getType() == StoreType.STATE && serializedState != null) {
                record.setStateValueType(serializedState.valueType())
                      .setStateValue(serializedState.value());
            }
            if (store.isWire() && serializedState != null) {
                record.setWireValue(serializedState.value());
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
        recordOutcome(ExecutionStatus.COMPLETED, null);
    }

    @Override
    public void runFailed(Throwable error) {
        finishRemainingRecords(ExecutionStatus.CANCELLED);
        recordOutcome(ExecutionStatus.FAILED, error.toString());
    }

    @Override
    public void runCancelled() {
        finishRemainingRecords(ExecutionStatus.CANCELLED);
        recordOutcome(ExecutionStatus.CANCELLED, null);
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
                         .onFailure(error -> log.error("Failed to persist run {}", jobRunId, error));
    }

    // The terminal write leaves what the platform keeps on the run as it is, since the run's record was
    // created whole at its start and may have been written since
    private void recordOutcome(ExecutionStatus status, String error) {
        if (runRecorded) {
            Date finished = new Date();
            Supplier<Future<Boolean>> write = () -> repository.recordOutcome(jobRunId, status, error, finished, "node " + jobRun.getNodeId());
            awaitWrite(() -> {
                Promise<Void> awaited = Promise.promise();
                writeOutcome(write, status, 1, awaited);
                return awaited.future();
            });
        }
    }

    // A run left RUNNING on a node that is still a member is ended by nothing else, so the write is
    // issued again until it lands or the node stops, which cancels the timer. The run itself waits
    // for the first attempts only.
    private void writeOutcome(Supplier<Future<Boolean>> write, ExecutionStatus status, int attempt, Promise<Void> awaited) {
        Future.<Boolean>future(promise -> write.get().onComplete(promise))
              .onSuccess(recorded -> {
                  if (!recorded) {
                      log.info("Run {} was ended while it executed and keeps that end, its outcome {} is not recorded", jobRunId, status);
                  }
                  awaited.tryComplete();
              })
              .onFailure(failure -> {
                  long delay = Math.min(OUTCOME_RETRY_MAX_MS, OUTCOME_RETRY_MS << Math.min(attempt - 1, 10));
                  log.error("Failed to persist the outcome {} of run {} on attempt {}, next attempt in {}ms",
                            status, jobRunId, attempt, delay, failure);
                  if (attempt >= OUTCOME_ATTEMPTS_AWAITED) {
                      awaited.tryComplete();
                  }
                  writeContext.owner().setTimer(delay, id -> writeOutcome(write, status, attempt + 1, awaited));
              });
    }

    private Future<TaskRecord> saveTask(TaskRecord record) {
        return repository.saveTask(record)
                         .onFailure(error -> log.error("Failed to persist task record {} of run {}",
                                                      record.getTaskPath(), jobRunId, error));
    }

    private Future<?> saveTasks(List<TaskRecord> records) {
        // distinct documents, so the writes go out together and the wait is one round trip
        return Future.join(records.stream().map(this::saveTask).toList());
    }

    /**
     * Issues the write on the recorder's context and blocks the calling run thread until it
     * has been acknowledged, failed or not: a write that fails is logged by the issuer, and what
     * the run does about it is the caller's to decide. The wait holds through cancellation of the
     * run, so the terminal records of a cancelled run still land before its stream terminates.
     *
     * @return why the write failed, or null when it was acknowledged
     */
    private Throwable awaitWrite(Supplier<Future<?>> write) {
        CompletableFuture<Throwable> acknowledged = new CompletableFuture<>();
        writeContext.runOnContext(v -> {
            Future<?> issued;
            try {
                issued = write.get();
            } catch (Throwable t) {
                // a write that throws while being issued completes the wait like one that failed, so
                // the run thread below can never be left waiting on a write that was never sent
                log.error("Failed to issue a ledger write for run {}", jobRunId, t);
                issued = Future.failedFuture(t);
            }
            issued.onComplete(ar -> acknowledged.complete(ar.cause()));
        });
        // join rather than get: an interrupt delivering cancellation must not abandon the write,
        // and join keeps the interrupt status set for the interpreter to act on
        return acknowledged.join();
    }

}
