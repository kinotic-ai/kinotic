package org.kinotic.orchestrator.internal.api.model.grind;

import lombok.extern.slf4j.Slf4j;
import org.kinotic.orchestrator.api.model.grind.ExecutionStatus;
import org.kinotic.orchestrator.api.model.grind.JobRun;
import org.kinotic.orchestrator.api.model.grind.StoreType;
import org.kinotic.orchestrator.api.model.grind.TaskRecord;
import org.kinotic.orchestrator.api.services.JobRunService;
import org.kinotic.orchestrator.api.services.TaskRecordService;
import org.kinotic.orchestrator.api.model.grind.JobDefinition;
import org.kinotic.orchestrator.api.model.grind.JobOwner;
import org.kinotic.orchestrator.api.model.grind.Result;
import org.kinotic.orchestrator.api.model.grind.StepCompletion;
import org.kinotic.orchestrator.api.model.grind.StepInfo;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Writes the {@link JobRun} and {@link TaskRecord}s for one job execution as its
 * {@link Result} stream emits step lifecycle events.
 */
@Slf4j
public class JobRunRecorder {

    private final String jobRunId;
    private final JobRun jobRun;
    private final JobRunService jobRunService;
    private final TaskRecordService taskRecordService;
    private final ObjectMapper objectMapper;

    private final Map<String, TaskRecord> recordsByPath = new ConcurrentHashMap<>();
    // Persistence calls are chained so writes for the same document can never race each other
    private CompletableFuture<Void> writeChain = CompletableFuture.completedFuture(null);

    public JobRunRecorder(String jobRunId,
                          JobDefinition jobDefinition,
                          JobOwner owner,
                          String resumedFrom,
                          JobRunService jobRunService,
                          TaskRecordService taskRecordService,
                          ObjectMapper objectMapper) {
        this.jobRunId = jobRunId;
        this.jobRunService = jobRunService;
        this.taskRecordService = taskRecordService;
        this.objectMapper = objectMapper;
        this.jobRun = new JobRun().setId(jobRunId)
                                  .setName(jobDefinition.getName())
                                  .setVersion(jobDefinition.getVersion())
                                  .setDescription(jobDefinition.getDescription())
                                  .setResumedFrom(resumedFrom);
        if(owner != null){
            jobRun.setOrganizationId(owner.getOrganizationId())
                  .setApplicationId(owner.getApplicationId())
                  .setProjectId(owner.getProjectId());
        }
    }

    public String getJobRunId() {
        return jobRunId;
    }

    /**
     * Records the owner once it is known, for a resume where the owner comes from the
     * original run loaded after recording has begun.
     */
    public void ownerResolved(String organizationId, String applicationId, String projectId) {
        jobRun.setOrganizationId(organizationId)
              .setApplicationId(applicationId)
              .setProjectId(projectId);
        enqueue(() -> jobRunService.save(jobRun).toCompletionStage().toCompletableFuture());
    }

    public void runStarted() {
        jobRun.setStatus(ExecutionStatus.RUNNING)
              .setStarted(new Date());
        enqueue(() -> jobRunService.save(jobRun).toCompletionStage().toCompletableFuture());
    }

    public void record(Result<?> result) {
        switch(result.getResultType()){
            case STEP_STARTED -> stepStarted(pathOf(result), (String) result.getValue());
            case STEP_COMPLETED -> stepCompleted(pathOf(result), (StepCompletion) result.getValue());
            case STEP_FAILED -> stepFailed(pathOf(result), (Throwable) result.getValue());
            case DYNAMIC_STEPS -> stepProducedDynamicSteps(pathOf(result));
            default -> { }
        }
    }

    private void stepProducedDynamicSteps(String stepPath) {
        TaskRecord record = recordsByPath.get(stepPath);
        if(record != null){
            record.setDynamicSteps(true);
            enqueue(() -> taskRecordService.save(record).toCompletionStage().toCompletableFuture());
        }
    }

    public void runCompleted() {
        jobRun.setStatus(ExecutionStatus.COMPLETED)
              .setFinished(new Date());
        enqueue(() -> jobRunService.save(jobRun).toCompletionStage().toCompletableFuture());
    }

    public void runFailed(Throwable throwable) {
        finishRemainingRecords(ExecutionStatus.CANCELLED);
        jobRun.setStatus(ExecutionStatus.FAILED)
              .setError(throwable.toString())
              .setFinished(new Date());
        enqueue(() -> jobRunService.save(jobRun).toCompletionStage().toCompletableFuture());
    }

    public void runCancelled() {
        finishRemainingRecords(ExecutionStatus.CANCELLED);
        jobRun.setStatus(ExecutionStatus.CANCELLED)
              .setFinished(new Date());
        enqueue(() -> jobRunService.save(jobRun).toCompletionStage().toCompletableFuture());
    }

    private void stepStarted(String stepPath, String description) {
        TaskRecord record = new TaskRecord().setId(jobRunId + ":" + stepPath)
                                            .setJobRunId(jobRunId)
                                            .setStepPath(stepPath)
                                            .setDescription(description)
                                            .setStatus(ExecutionStatus.RUNNING)
                                            .setStarted(new Date());
        recordsByPath.put(stepPath, record);
        enqueue(() -> taskRecordService.save(record).toCompletionStage().toCompletableFuture());
    }

    private void stepCompleted(String stepPath, StepCompletion completion) {
        TaskRecord record = recordsByPath.get(stepPath);
        if(record == null){
            log.warn("STEP_COMPLETED for unknown step path {} in run {}", stepPath, jobRunId);
        }else{
            record.setStatus(ExecutionStatus.COMPLETED)
                  .setFinished(new Date())
                  .setStoreType(completion.getStoreType())
                  .setResultName(completion.getStoredName());
            if(completion.getStoreType() == StoreType.STATE){
                // STATE is a contract: the value must be persistable, or the run fails here and now
                if(completion.getStoredValue() == null){
                    failStep(record, "Step " + stepPath + " (" + record.getDescription()
                             + ") is declared taskStoreState but produced no value", null);
                }
                // Type erasure makes any generic value unrestorable: the record can only capture the
                // runtime class (ArrayList, Optional, ...), not its type arguments, so replay would
                // deserialize the contents as Maps and downstream injection would fail far from the
                // cause. Checking declared type parameters catches every such class in one rule while
                // letting reified subclasses (class WidgetList extends ArrayList<Widget>) through,
                // since their bindings survive erasure and round-trip correctly.
                Class<?> valueClass = completion.getStoredValue().getClass();
                if(valueClass.getTypeParameters().length > 0){
                    failStep(record, "Step " + stepPath + " (" + record.getDescription()
                             + ") is declared taskStoreState but produced a " + valueClass.getName()
                             + ", a generic type. Generic values such as List, Map, and Optional cannot be"
                             + " stored as STATE because Java erases their type arguments, so the value"
                             + " could not be restored correctly when the run is resumed. Either wrap the"
                             + " value in a domain class (a field like 'List<Workload> workloads' keeps its"
                             + " element type and round-trips correctly), or if the value can be re-fetched"
                             + " from its source of truth, use taskStoreResult so the step reloads on"
                             + " resume instead", null);
                }
                record.setResultValueType(valueClass.getName());
                try {
                    record.setResultValue(objectMapper.valueToTree(completion.getStoredValue()));
                } catch (Exception e) {
                    failStep(record, "Step " + stepPath + " (" + record.getDescription()
                             + ") is declared taskStoreState but its value of type "
                             + completion.getStoredValue().getClass().getName() + " is not serializable", e);
                }
            }
            enqueue(() -> taskRecordService.save(record).toCompletionStage().toCompletableFuture());
        }
    }

    /**
     * Persists the record as FAILED and throws, failing the run at this step.
     * Thrown from record(), the exception propagates through the result stream's doOnNext
     * as the run's error signal.
     */
    private void failStep(TaskRecord record, String message, Exception cause) {
        record.setStatus(ExecutionStatus.FAILED)
              .setError(message)
              .setFinished(new Date());
        enqueue(() -> taskRecordService.save(record).toCompletionStage().toCompletableFuture());
        throw new IllegalStateException(message, cause);
    }

    private void stepFailed(String stepPath, Throwable throwable) {
        TaskRecord record = recordsByPath.get(stepPath);
        if(record == null){
            log.warn("STEP_FAILED for unknown step path {} in run {}", stepPath, jobRunId);
        }else{
            record.setStatus(ExecutionStatus.FAILED)
                  .setError(throwable.toString())
                  .setFinished(new Date());
            enqueue(() -> taskRecordService.save(record).toCompletionStage().toCompletableFuture());
        }
    }

    /**
     * Marks every record still RUNNING with the given terminal status. Used when the run
     * terminates abnormally and in-flight steps will never report completion.
     */
    private void finishRemainingRecords(ExecutionStatus status) {
        for(TaskRecord record : recordsByPath.values()){
            if(record.getStatus() == ExecutionStatus.RUNNING){
                record.setStatus(status)
                      .setFinished(new Date());
                enqueue(() -> taskRecordService.save(record).toCompletionStage().toCompletableFuture());
            }
        }
    }

    private String pathOf(Result<?> result) {
        Deque<Integer> sequences = new ArrayDeque<>();
        for(StepInfo info = result.getStepInfo(); info != null; info = info.getAncestor()){
            sequences.addFirst(info.getSequence());
        }
        return sequences.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining("/"));
    }

    private synchronized void enqueue(Supplier<CompletableFuture<?>> writeOperation) {
        writeChain = writeChain.thenCompose(v -> writeOperation.get()
                                                               .handle((r, t) -> {
                                                                   if(t != null){
                                                                       log.warn("Failed to persist record for run {}", jobRunId, t);
                                                                   }
                                                                   return null;
                                                               }));
    }

}
