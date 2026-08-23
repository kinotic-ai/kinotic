package org.kinotic.system.internal.api.model.grind;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.grind.ExecutionStatus;
import org.kinotic.domain.api.model.grind.JobRun;
import org.kinotic.domain.api.model.grind.StoreType;
import org.kinotic.domain.api.model.grind.TaskRecord;
import org.kinotic.domain.api.services.JobRunService;
import org.kinotic.domain.api.services.TaskRecordService;
import org.kinotic.system.api.model.grind.JobDefinition;
import org.kinotic.domain.api.model.grind.JobOwner;
import org.kinotic.system.api.model.grind.Result;
import org.kinotic.system.api.model.grind.StepCompletion;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Writes the {@link JobRun} and {@link TaskRecord}s for one job execution as its
 * {@link Result} stream emits step lifecycle events.
 */
@Slf4j
public class JobRunRecorder {

    private final String jobRunId;
    private final JobRun jobRun;
    private final JobDefinition jobDefinition;
    private final JobRunService jobRunService;
    private final TaskRecordService taskRecordService;
    private final ObjectMapper objectMapper;

    private final Map<String, TaskRecord> recordsByPath = new ConcurrentHashMap<>();
    // Persistence calls are chained so writes for the same document can never race each other
    private Future<Void> writeChain = Future.succeededFuture();

    public JobRunRecorder(String jobRunId,
                          JobDefinition jobDefinition,
                          JobOwner owner,
                          String resumedFrom,
                          JobRunService jobRunService,
                          TaskRecordService taskRecordService,
                          ObjectMapper objectMapper) {
        this.jobRunId = jobRunId;
        this.jobDefinition = jobDefinition;
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
        enqueue(() -> jobRunService.save(jobRun));
    }

    public void runStarted() {
        jobRun.setStatus(ExecutionStatus.RUNNING)
              .setStarted(new Date());
        enqueue(() -> jobRunService.save(jobRun));
        // Discoverable structure at start is the definition's static step tree; dynamic
        // subtrees are seeded by stepProducedDynamicSteps as tasks reveal them
        seedDiscovered(discoveredStepRecords(jobRunId, "", new JobDefinitionStep(0, jobDefinition)));
    }

    public void record(Result<?> result) {
        switch(result.getResultType()){
            case STEP_STARTED -> stepStarted(result.getStepInfo().path(), (String) result.getValue());
            case STEP_COMPLETED -> stepCompleted(result.getStepInfo().path(), (StepCompletion) result.getValue());
            case STEP_FAILED -> stepFailed(result.getStepInfo().path(), (Throwable) result.getValue());
            case DYNAMIC_STEPS -> stepProducedDynamicSteps(result.getStepInfo().path(), (Step) result.getValue());
            default -> { }
        }
    }

    private void stepProducedDynamicSteps(String stepPath, Step dynamicStep) {
        TaskRecord record = recordsByPath.get(stepPath);
        if(record != null){
            record.setDynamicSteps(true);
            enqueue(() -> taskRecordService.save(record));
        }
        seedDiscovered(discoveredStepRecords(jobRunId, stepPath, dynamicStep));
    }

    /**
     * Builds one PENDING {@link TaskRecord} for the given step and each of its static
     * descendants, rooted under {@code parentPath}. Dynamic descendants are unknowable until
     * their tasks execute; each later discovery walks only the newly revealed subtree.
     * @param jobRunId the run the records belong to
     * @param parentPath the path of the step that revealed this subtree, empty for the run's root
     * @param step the revealed step
     * @return the records, in discovery order
     */
    public static List<TaskRecord> discoveredStepRecords(String jobRunId, String parentPath, Step step) {
        List<TaskRecord> ret = new ArrayList<>();
        collectDiscovered(jobRunId, parentPath, step, ret);
        return ret;
    }

    private static void collectDiscovered(String jobRunId, String parentPath, Step step, List<TaskRecord> collected) {
        String stepPath = parentPath.isEmpty() ? String.valueOf(step.getSequence())
                                               : parentPath + "/" + step.getSequence();
        collected.add(new TaskRecord().setId(jobRunId + ":" + stepPath)
                                      .setJobRunId(jobRunId)
                                      .setStepPath(stepPath)
                                      .setDescription(step.getDescription())
                                      .setStatus(ExecutionStatus.PENDING));
        if(step instanceof JobDefinitionStep definitionStep){
            for(Step child : definitionStep.getSteps()){
                collectDiscovered(jobRunId, stepPath, child, collected);
            }
        }
    }

    private void seedDiscovered(List<TaskRecord> discovered) {
        for(TaskRecord record : discovered){
            recordsByPath.put(record.getStepPath(), record);
            enqueue(() -> taskRecordService.save(record));
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
        // Normally seeded PENDING at discovery; creating here is load-bearing against a step
        // shape the discovery walk cannot see, so its lifecycle is still recorded
        TaskRecord record = recordsByPath.computeIfAbsent(stepPath,
                                                          path -> new TaskRecord().setId(jobRunId + ":" + path)
                                                                                  .setJobRunId(jobRunId)
                                                                                  .setStepPath(path));
        record.setDescription(description)
              .setStatus(ExecutionStatus.RUNNING)
              .setStarted(new Date());
        enqueue(() -> taskRecordService.save(record));
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
            enqueue(() -> taskRecordService.save(record));
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
        enqueue(() -> taskRecordService.save(record));
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

    private synchronized void enqueue(Supplier<Future<?>> writeOperation) {
        writeChain = writeChain.compose(v -> writeOperation.get()
                                                           .transform(ar -> {
                                                               if(ar.failed()){
                                                                   log.warn("Failed to persist record for run {}", jobRunId, ar.cause());
                                                               }
                                                               return Future.succeededFuture();
                                                           }));
    }

}
