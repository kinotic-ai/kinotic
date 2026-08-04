package org.kinotic.orchestrator.internal.api.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.grind.ExecutionStatus;
import org.kinotic.domain.api.model.grind.JobRun;
import org.kinotic.domain.api.model.grind.TaskRecord;
import org.kinotic.domain.api.services.JobRunService;
import org.kinotic.domain.api.services.TaskRecordService;
import org.kinotic.orchestrator.api.grind.Result;
import org.kinotic.orchestrator.api.grind.StepCompletion;
import org.kinotic.orchestrator.api.grind.StepInfo;
import org.kinotic.orchestrator.api.pipeline.Pipeline;
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
 * Writes the {@link JobRun} and {@link TaskRecord}s for one pipeline execution as its
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
                          Pipeline pipeline,
                          String jobDescription,
                          JobRunService jobRunService,
                          TaskRecordService taskRecordService,
                          ObjectMapper objectMapper) {
        this.jobRunId = jobRunId;
        this.jobRunService = jobRunService;
        this.taskRecordService = taskRecordService;
        this.objectMapper = objectMapper;
        this.jobRun = new JobRun().setId(jobRunId)
                                  .setPipeline(pipeline.getName())
                                  .setPipelineVersion(pipeline.getVersion())
                                  .setDescription(jobDescription);
    }

    public void runStarted() {
        jobRun.setStatus(ExecutionStatus.RUNNING)
              .setStarted(new Date());
        enqueue(() -> jobRunService.save(jobRun));
    }

    public void record(Result<?> result) {
        switch(result.getResultType()){
            case STEP_STARTED -> stepStarted(pathOf(result), (String) result.getValue());
            case STEP_COMPLETED -> stepCompleted(pathOf(result), (StepCompletion) result.getValue());
            case STEP_FAILED -> stepFailed(pathOf(result), (Throwable) result.getValue());
            default -> { }
        }
    }

    public void runCompleted() {
        jobRun.setStatus(ExecutionStatus.COMPLETED)
              .setFinished(new Date());
        enqueue(() -> jobRunService.save(jobRun));
    }

    public void runFailed(Throwable throwable) {
        finishRemainingRecords(ExecutionStatus.CANCELLED);
        jobRun.setStatus(ExecutionStatus.FAILED)
              .setError(throwable.toString())
              .setFinished(new Date());
        enqueue(() -> jobRunService.save(jobRun));
    }

    public void runCancelled() {
        finishRemainingRecords(ExecutionStatus.CANCELLED);
        jobRun.setStatus(ExecutionStatus.CANCELLED)
              .setFinished(new Date());
        enqueue(() -> jobRunService.save(jobRun));
    }

    private void stepStarted(String stepPath, String description) {
        TaskRecord record = new TaskRecord().setId(jobRunId + ":" + stepPath)
                                            .setJobRunId(jobRunId)
                                            .setStepPath(stepPath)
                                            .setDescription(description)
                                            .setStatus(ExecutionStatus.RUNNING)
                                            .setStarted(new Date());
        recordsByPath.put(stepPath, record);
        enqueue(() -> taskRecordService.save(record));
    }

    private void stepCompleted(String stepPath, StepCompletion completion) {
        TaskRecord record = recordsByPath.get(stepPath);
        if(record == null){
            log.warn("STEP_COMPLETED for unknown step path {} in run {}", stepPath, jobRunId);
        }else{
            record.setStatus(ExecutionStatus.COMPLETED)
                  .setFinished(new Date());
            if(completion.getStoredValue() != null){
                record.setResultName(completion.getStoredName())
                      .setResultValueType(completion.getStoredValue().getClass().getName());
                try {
                    record.setResultValue(objectMapper.valueToTree(completion.getStoredValue()));
                } catch (Exception e) {
                    // An unserializable stored value costs replay of this step, not the run
                    log.warn("Could not serialize stored result for step {} in run {}", stepPath, jobRunId, e);
                }
            }
            enqueue(() -> taskRecordService.save(record));
        }
    }

    private void stepFailed(String stepPath, Throwable throwable) {
        TaskRecord record = recordsByPath.get(stepPath);
        if(record == null){
            log.warn("STEP_FAILED for unknown step path {} in run {}", stepPath, jobRunId);
        }else{
            record.setStatus(ExecutionStatus.FAILED)
                  .setError(throwable.toString())
                  .setFinished(new Date());
            enqueue(() -> taskRecordService.save(record));
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
                enqueue(() -> taskRecordService.save(record));
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
